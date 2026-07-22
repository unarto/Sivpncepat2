/* libssh2_config.h - Android NDK Configuration */
#ifndef LIBSSH2_CONFIG_H
#define LIBSSH2_CONFIG_H

/* Android Platform Detection */
#ifdef __ANDROID__
  #define HAVE_UNISTD_H 1
  #define HAVE_INTTYPES_H 1
  #define HAVE_SYS_TIME_H 1
  #define HAVE_GETTIMEOFDAY 1
  #define HAVE_STRTOLL 1
  #define HAVE_SNPRINTF 1
  #define HAVE_SELECT 1
  #define HAVE_POLL 1
  #define HAVE_SYS_SOCKET_H 1
  #define HAVE_SYS_SELECT_H 1
  #define HAVE_SYS_UIO_H 1
  #define HAVE_SYS_IOCTL_H 1
  #define HAVE_ARPA_INET_H 1
  #define HAVE_NETINET_IN_H 1
  #define HAVE_EXPLICIT_BZERO 1
#endif

/* Crypto Backend - Use Mbed TLS */
#define LIBSSH2_MBEDTLS 1

/* Features */
#define HAVE_LIBMBEDTLS 1
#define LIBSSH2_HAVE_ZLIB 0
#define LIBSSH2_DH_GEX_NEW 1

/* Socket API */
#define HAVE_SOCKET 1
#define HAVE_INET_ADDR 1

/* String Functions */
#define HAVE_STRDUP 1
#define HAVE_STRCHR 1
#define HAVE_STRLEN 1

/* Memory Functions */
#define HAVE_MALLOC 1
#define HAVE_FREE 1
#define HAVE_REALLOC 1
#define HAVE_MEMCPY 1
#define HAVE_MEMMOVE 1
#define HAVE_MEMSET 1

/* Include files */
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <sys/time.h>

/* Android-specific defines */
#define _GNU_SOURCE 1
#define LIBSSH2_DISABLE_INSTALL 1

#endif /* LIBSSH2_CONFIG_H */
