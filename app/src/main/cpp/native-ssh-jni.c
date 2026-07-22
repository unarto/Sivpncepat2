#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <pthread.h>
#include <errno.h>
#include <libssh2.h>

#define LOG_TAG "NativeSshC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static volatile int ssh_running = 0;
static int server_fd = -1;
static LIBSSH2_SESSION *global_session = NULL;
static int global_sock = -1;

static void release_start_strings(
    JNIEnv *env,
    jstring host,
    const char *c_host,
    jstring username,
    const char *c_user,
    jstring password,
    const char *c_pass
) {
    if (c_host != NULL) {
        (*env)->ReleaseStringUTFChars(env, host, c_host);
    }
    if (c_user != NULL) {
        (*env)->ReleaseStringUTFChars(env, username, c_user);
    }
    if (c_pass != NULL) {
        (*env)->ReleaseStringUTFChars(env, password, c_pass);
    }
}

static int connect_tcp(const char *host, int port) {
    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", port);

    struct addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

    struct addrinfo *result = NULL;
    int rc = getaddrinfo(host, port_str, &hints, &result);
    if (rc != 0) {
        LOGE("getaddrinfo failed for %s:%d: %s", host, port, gai_strerror(rc));
        return -1;
    }

    int sock = -1;
    for (struct addrinfo *rp = result; rp != NULL; rp = rp->ai_next) {
        sock = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (sock == -1) {
            continue;
        }

        struct timeval timeout;
        timeout.tv_sec = 15;
        timeout.tv_usec = 0;
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

        if (connect(sock, rp->ai_addr, rp->ai_addrlen) == 0) {
            break;
        }

        close(sock);
        sock = -1;
    }

    freeaddrinfo(result);
    return sock;
}

struct ThreadArgs {
    int client_fd;
    LIBSSH2_SESSION *session;
};

// Thread to pipe data between local socket and SSH channel
void *pipe_thread(void *args) {
    struct ThreadArgs *ta = (struct ThreadArgs *)args;
    int client_fd = ta->client_fd;
    LIBSSH2_SESSION *session = ta->session;
    free(ta);

    LOGI("New client connection accepted, establishing direct-tcpip channel...");
    LIBSSH2_CHANNEL *channel = libssh2_channel_direct_tcpip_ex(session, "127.0.0.1", 1080, "127.0.0.1", 0);
    if (!channel) {
        LOGE("Failed to open direct-tcpip channel");
        close(client_fd);
        return NULL;
    }

    char buffer[4096];
    fd_set fds;
    int max_fd = client_fd;

    while (ssh_running) {
        FD_ZERO(&fds);
        FD_SET(client_fd, &fds);
        
        struct timeval tv = {1, 0}; // 1 second timeout for responsiveness
        int rc = select(max_fd + 1, &fds, NULL, NULL, &tv);

        if (rc < 0) {
            break; // select error
        }

        // Try reading from client_fd and sending to channel
        if (FD_ISSET(client_fd, &fds)) {
            ssize_t nread = recv(client_fd, buffer, sizeof(buffer), 0);
            if (nread <= 0) {
                break; // client disconnected
            }
            ssize_t nwritten = 0;
            while (nwritten < nread) {
                ssize_t nw = libssh2_channel_write(channel, buffer + nwritten, nread - nwritten);
                if (nw < 0) {
                    break; // write error
                }
                nwritten += nw;
            }
        }

        // Read from channel and send to client_fd (non-blocking style check)
        ssize_t nread_ssh = libssh2_channel_read(channel, buffer, sizeof(buffer));
        if (nread_ssh > 0) {
            send(client_fd, buffer, nread_ssh, 0);
        } else if (nread_ssh == LIBSSH2_ERROR_EAGAIN) {
            // No data available yet
        } else if (nread_ssh < 0) {
            break; // error or EOF
        }
    }

    libssh2_channel_free(channel);
    close(client_fd);
    LOGI("Client pipe thread finished");
    return NULL;
}

JNIEXPORT jint JNICALL 
Java_com_sivpn_cepat_vpn_NativeSshTunnel_startSshTunnel(
    JNIEnv *env, 
    jclass clazz, 
    jstring host, 
    jint port, 
    jstring username, 
    jstring password, 
    jint socksPort
) {
    if (host == NULL || username == NULL || password == NULL || port < 1 || port > 65535 || socksPort < 1 || socksPort > 65535) {
        LOGE("Invalid JNI arguments for startSshTunnel");
        return -10;
    }

    const char *c_host = (*env)->GetStringUTFChars(env, host, NULL);
    const char *c_user = (*env)->GetStringUTFChars(env, username, NULL);
    const char *c_pass = (*env)->GetStringUTFChars(env, password, NULL);

    if (c_host == NULL || c_user == NULL || c_pass == NULL || c_host[0] == '\0' || c_user[0] == '\0') {
        LOGE("Failed to convert required JNI strings");
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -11;
    }

    LOGI("Starting SSH Tunnel targeting %s:%d (User: %s)", c_host, port, c_user);

    int rc = libssh2_init(0);
    if (rc != 0) {
        LOGE("libssh2 initialization failed (%d)", rc);
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -1;
    }

    int sock = connect_tcp(c_host, port);
    if (sock == -1) {
        LOGE("Failed to connect to SSH server %s:%d", c_host, port);
        libssh2_exit();
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -3;
    }
    global_sock = sock;

    LIBSSH2_SESSION *session = libssh2_session_init();
    if (!session) {
        LOGE("Failed to initialize LIBSSH2 session");
        close(sock);
        libssh2_exit();
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -4;
    }
    global_session = session;

    // Use blocking mode on session for simplicity during setup
    libssh2_session_set_blocking(session, 1);

    rc = libssh2_session_handshake(session, sock);
    if (rc != 0) {
        LOGE("SSH handshake failed (%d)", rc);
        libssh2_session_free(session);
        close(sock);
        libssh2_exit();
        global_session = NULL;
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -5;
    }

    rc = libssh2_userauth_password(session, c_user, c_pass);
    if (rc != 0) {
        LOGE("SSH authentication failed for user %s", c_user);
        libssh2_session_free(session);
        close(sock);
        libssh2_exit();
        global_session = NULL;
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -6;
    }

    LOGI("SSH Authentication successful! Setting up local SOCKS forwarder server on port %d...", socksPort);

    // Setup local listener
    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd == -1) {
        LOGE("Failed to create local server socket");
        libssh2_session_free(session);
        close(sock);
        libssh2_exit();
        global_session = NULL;
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -7;
    }

    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in local_sin;
    local_sin.sin_family = AF_INET;
    local_sin.sin_addr.s_addr = htonl(INADDR_LOOPBACK); // loopback only for safety
    local_sin.sin_port = htons(socksPort);

    if (bind(server_fd, (struct sockaddr *)&local_sin, sizeof(local_sin)) < 0) {
        LOGE("Failed to bind local SOCKS server to port %d", socksPort);
        close(server_fd);
        server_fd = -1;
        libssh2_session_free(session);
        close(sock);
        libssh2_exit();
        global_session = NULL;
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -8;
    }

    if (listen(server_fd, 10) < 0) {
        LOGE("Failed to listen on local SOCKS server");
        close(server_fd);
        server_fd = -1;
        libssh2_session_free(session);
        close(sock);
        libssh2_exit();
        global_session = NULL;
        global_sock = -1;
        release_start_strings(env, host, c_host, username, c_user, password, c_pass);
        return -9;
    }

    ssh_running = 1;
    LOGI("Local SOCKS listening loop started on 127.0.0.1:%d", socksPort);

    while (ssh_running) {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);
        int client_fd = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);
        if (client_fd < 0) {
            if (ssh_running) {
                LOGE("Accept error in local SOCKS server");
                usleep(100000); // Back off
            }
            continue;
        }

        // Spawn a thread to handle this connection
        pthread_t tid;
        struct ThreadArgs *ta = (struct ThreadArgs *)malloc(sizeof(struct ThreadArgs));
        if (ta == NULL) {
            LOGE("Failed to allocate pipe thread arguments");
            close(client_fd);
            continue;
        }
        ta->client_fd = client_fd;
        ta->session = session;
        if (pthread_create(&tid, NULL, pipe_thread, ta) != 0) {
            LOGE("Failed to create pipe thread");
            close(client_fd);
            free(ta);
        } else {
            pthread_detach(tid);
        }
    }

    LOGI("SSH Tunnel stopped cleanly");
    if (server_fd != -1) {
        close(server_fd);
        server_fd = -1;
    }
    libssh2_session_disconnect(session, "SSH Tunnel closing");
    libssh2_session_free(session);
    global_session = NULL;
    close(sock);
    global_sock = -1;
    libssh2_exit();

    release_start_strings(env, host, c_host, username, c_user, password, c_pass);
    return 0;
}

JNIEXPORT void JNICALL 
Java_com_sivpn_cepat_vpn_NativeSshTunnel_stopSshTunnel(
    JNIEnv *env, 
    jclass clazz
) {
    LOGI("Requesting components shutdown...");
    ssh_running = 0;
    if (server_fd != -1) {
        shutdown(server_fd, SHUT_RDWR);
        close(server_fd);
        server_fd = -1;
    }
    if (global_sock != -1) {
        shutdown(global_sock, SHUT_RDWR);
        close(global_sock);
        global_sock = -1;
    }
}
