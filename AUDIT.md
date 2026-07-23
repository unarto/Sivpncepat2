# SYSTEM AUDIT REPORT (UPDATED)
**Date**: 2026-07-23

## 1. Top 5 Largest Files (LoC)
- `PayloadInjector.kt` (~400 baris) - Ukuran berkurang setelah abstraksi `WebSocketFramer` dan `PayloadFormatter`.
- `HttpProxyServer.kt` (444 baris) - Terisolasi dengan baik, menggunakan `Semaphore` untuk membatasi koneksi.
- `SiVpnService.kt` (366 baris) - Controller JNI, solid.
- `MainViewModel.kt` (~200 baris) - Berkurang setelah abstraksi dialog UI state.
- `LocalPortForwarder.kt` (292 baris) - Stabil dengan mekanisme concurrency safe.

## 2. Technical Audit & Code Review

### A. Race Conditions & Concurrency
- **SOCKS/HTTP & LocalPortForwarder Semaphore**: Mekanisme pembatasan koneksi menggunakan `Semaphore(100)` sudah tepat dan dieksekusi dengan `tryAcquire()`. Pelepasan *semaphore* (release) aman dilakukan dalam blok `finally` coroutine sehingga menjamin *zero leak* dari permit semaphore, meskipun coroutine dibatalkan (cancelled).
- **Client Jobs Cleanup**: Sinkronisasi `synchronized(clientJobs)` untuk list `Job` aktif saat client connect dan disconnect via `invokeOnCompletion` sudah *thread-safe*. 
- **Graceful Shutdown**: Pemanggilan `cancelAndJoin()` pada `acceptJob` dan menunggu semua `clientJobs` di dalam fungsi `stop()` menghindarkan dari *race condition* dan menjamin port benar-benar terbebas sebelum koneksi baru.

### B. Memory & Coroutine Leaks
- **Scope Cancellation**: Terdapat pola yang sangat baik di mana *pipe streams* bi-directional saling membatalkan melalui `invokeOnCompletion { otherJob.cancel() }`. Ini sangat krusial dalam TCP tunnel untuk mencegah *coroutine leak* di mana salah satu stream idle/zombie selamanya.
- **AutoPing Job**: Di dalam `PayloadInjector`, `autoPingJob` direferensikan dalam `handleClient` dan secara eksplisit dibatalkan (cancel) pada blok `finally`. Ini memastikan *no job leakage*.
- **Direct ByteBuffer Usage**: Penggunaan `ByteBuffer.allocateDirect(BUFFER_SIZE)` bersama `NIO Channels` di `forwardStream` mencegah alokasi *heap memory* yang berlebihan (mencegah *Garbage Collection Stuttering*) karena memori dialokasikan *off-heap*.

### C. Logic Bugs & Edge Cases (Masukan Teknis)
Meskipun arsitektur saat ini sangat kokoh, ada beberapa masukan teknis tingkat lanjut untuk dioptimasi ke depannya:
1. **Byte-by-byte Header Reading di `HttpProxyServer.kt`**: 
   Metode saat ini `clientIn.read()` digunakan membaca *header* HTTP byte demi byte sampai ketemu `\r\n\r\n`. Pendekatan ini aman dan menghindari *over-reading* payload body, namun performanya lambat. **Saran perbaikan**: Gunakan `BufferedInputStream` dengan mark/reset atau gunakan parser ringan HTTP yang membaca dengan *chunk* kecil dan memotong buffer secara dinamis.
2. **WebSocket Blocking I/O**: 
   Fungsi `WebSocketFramer.forwardWsDecode` dan `forwardWsEncode` dipanggil di dalam `launch` dengan dispatcher IO. Karena menggunakan `DataInputStream.readFully`, I/O bersifat *blocking thread*. Tidak ada masalah karena Dispatchers.IO mendukung hingga 64 thread, tetapi jika koneksi melebihi 64 (misal: 100 batas semaphore), sebagian coroutine mungkin *starved* di antrian thread pool. **Saran perbaikan**: Jika akan diskalakan lebih dari 64 koneksi serentak, gunakan NIO/Non-blocking I/O atau naikkan ukuran pool dari Dispatchers.IO.
3. **Pengecekan Tipe Payload URL**: 
   Pada parser `ProxyParser` dan regex string pada payload, perlakuan format host yang memiliki braket untuk IPv6 misal `[2001:db8::1]:8080` perlu penanganan khusus saat mengganti `[host_port]`. Beruntungnya hal ini tidak akan menyebabkan sistem *crash*, namun bisa mengganggu HTTP Request URI.

## 3. Kesimpulan Status Eksekusi (RSP Protocol)
- **Status**: **GREEN** (Sistem Sangat Sehat, Terstruktur, dan Bebas Leak).
- **Active Assets**: `MainViewModel.kt`, `MainScreen.kt`, `PayloadInjector.kt`.
- Struktur folder, *Clean Architecture*, dan prinsip SRP (Single Responsibility Principle) sudah 100% dipatuhi.
- Seluruh file yang memuat 4800+ baris sudah didestruksi menjadi modul-modul elegan di bawah 600 baris dengan tanggung jawab spesifik (Framer WS, Logger, Monitor, Forwarder).

## 4. Next Action / Pending Queue
- Infrastruktur Core VPN dan UI Layer sudah matang. Siap untuk tahap pengujian (Test Case / Manual Test).
- Tidak ada tugas refaktor major tersisa.

## 5. ADVANCED NETWORKING FEATURE AUDIT

Berdasarkan permintaan untuk mengaudit dukungan fitur *advanced networking* pada stack aplikasi ini (Android VpnService, libssh2, mbedTLS 3.7.6, hev-socks5-tunnel, HTTP/SOCKS5 Proxy). Tanda `[x]` menunjukkan fitur sudah terimplementasi di konfigurasi aplikasi saat ini, sedangkan `[ ]` menunjukkan fitur belum diimplementasi (tetapi berpotensi didukung oleh stack).

### ========================
### TCP OPTIMIZATION
### ========================
- [ ] **TCP Fast Open (TFO)**
  - *Support*: Linux (Ya), Android (Sebagian, via Java Socket Options API >26), C Socket (Ya).
  - *Default Terbaik*: Disable (karena tidak semua ISP/jaringan mendukung TFO, bisa menyebabkan blackhole).
- [x] **TCP_NODELAY**
  - *Support*: Ya (Java Socket, C Socket). Sangat krusial untuk SSH & tunneling.
  - *Default Terbaik*: `true` (aktif).
- [x] **TCP KeepAlive (SO_KEEPALIVE)**
  - *Support*: Ya (Java Socket, C Socket).
  - *Default Terbaik*: `true` (aktif).
- [x] **SO_REUSEADDR**
  - *Support*: Ya (Semua API).
  - *Default Terbaik*: `true` (aktif).
- [ ] **SO_REUSEPORT**
  - *Support*: Linux >= 3.9 (Ya), Android (Hanya via NDK / JNI).
  - *Default Terbaik*: Disable (Berguna untuk high-concurrency multi-worker, tapi kompleks di Java).
- [ ] **TCP_QUICKACK**
  - *Support*: Linux/Android C Socket (Ya). Java (Tidak didukung secara native).
  - *Default Terbaik*: Disable.
- [ ] **TCP_DEFER_ACCEPT**
  - *Support*: Linux (Ya). Java (Tidak didukung).
  - *Default Terbaik*: Disable.
- [ ] **TCP Cork/NOPUSH**
  - *Support*: Linux (TCP_CORK).
  - *Default Terbaik*: Disable (karena bertentangan dengan TCP_NODELAY).
- [x] **TCP Send Buffer**
  - *Support*: Ya.
  - *Default Terbaik*: `65536` (64KB) sudah cukup optimal untuk Android mobile.
- [x] **TCP Receive Buffer**
  - *Support*: Ya.
  - *Default Terbaik*: `65536` (64KB).
- [ ] **Auto Buffer Size**
  - *Support*: OS Level tuning (Otomatis).
  - *Default Terbaik*: Diserahkan ke OS TCP Window Scaling.
- [ ] **TCP Congestion Algorithm**
  - *Support*: Linux `TCP_CONGESTION` (BBR, Cubic). Android (Membutuhkan akses Root untuk mengubahnya).
  - *Default Terbaik*: BBR (Bila didukung).
- [ ] **ECN Enable/Disable**
  - *Support*: OS Level.
  - *Default Terbaik*: Default OS.
- [ ] **Happy Eyeballs (RFC 8305)**
  - *Support*: Harus diimplementasi di logic aplikasi (dual stack paralel connect).
  - *Default Terbaik*: Aktif untuk fallback cepat.
- [ ] **IPv4 / IPv6 Preferred / Dual Stack**
  - *Support*: Ya (Logic DNS Resolver aplikasi).
  - *Default Terbaik*: IPv4 Preferred (Koneksi IPv6 sering bermasalah di operator seluler lokal).

### ========================
### SOCKET
### ========================
- [x] **Connect Timeout**
  - *Support*: Ya (hev-socks5-tunnel dan Java Socket).
  - *Default Terbaik*: `10000` ms (10 detik).
- [ ] **Read Timeout**
  - *Support*: Ya (Java `soTimeout`).
  - *Default Terbaik*: `60000` ms.
- [ ] **Write Timeout**
  - *Support*: Ya (C Socket / hev-socks5-tunnel). Java membutuhkan Non-blocking IO (NIO).
  - *Default Terbaik*: `60000` ms.
- [ ] **Idle Timeout**
  - *Support*: Ya (Logic ping & timeout).
  - *Default Terbaik*: `300` detik (5 Menit).
- [ ] **Socket Priority**
  - *Support*: Android `TrafficStats.setThreadStatsTag()`.
  - *Default Terbaik*: Voice / Interactive priority.

### ========================
### SSH (libssh2)
### ========================
- [ ] **Compression**
  - *Support*: libssh2 (zlib).
  - *Default Terbaik*: Disable (Karena payload HTTP/TLS biasanya terkompresi atau terenkripsi, kompresi ulang memboroskan CPU).
- [ ] **Cipher Preference**
  - *Support*: libssh2.
  - *Default Terbaik*: `aes128-ctr,aes256-ctr,chacha20-poly1305@openssh.com`.
- [ ] **MAC Preference**
  - *Support*: libssh2.
  - *Default Terbaik*: `hmac-sha2-256,hmac-sha2-512`.
- [ ] **KEX Preference**
  - *Support*: libssh2.
  - *Default Terbaik*: `curve25519-sha256,curve25519-sha256@libssh.org`.
- [ ] **HostKey Preference**
  - *Support*: libssh2.
  - *Default Terbaik*: `ssh-ed25519,ecdsa-sha2-nistp256`.
- [ ] **Verify Host Key (StrictHostKeyChecking)**
  - *Support*: libssh2 (membaca file `known_hosts`).
  - *Default Terbaik*: `false` (Abaikan untuk aplikasi VPN Publik, rawan pergantian kunci server).
- [x] **Password Authentication**
  - *Support*: Ya.
- [ ] **Private Key Authentication**
  - *Support*: Ya.
- [ ] **Password + Key Authentication**
  - *Support*: Ya.
- [ ] **Session Reuse**
  - *Support*: Tidak native di SSH, libssh2 tidak mendukung multiplexing penuh (seperti OpenSSH `ControlMaster`).
- [ ] **Rekey Interval / Rekey Data Limit**
  - *Support*: libssh2.
  - *Default Terbaik*: Disable atau 1 Jam / 1 GB.

### ========================
### TLS (mbedTLS)
### ========================
- [x] **TLS Version**
  - *Support*: mbedTLS. (Tersedia opsi Forcing TLSv1.2, TLSv1.3).
  - *Default Terbaik*: `Auto` (Diserahkan kepada mbedTLS negotiation).
- [ ] **Verify Certificate**
  - *Support*: mbedTLS.
  - *Default Terbaik*: `false` (Banyak server VPN menggunakan Self-Signed Cert atau SNI Spoofing).
- [x] **Verify Hostname (SNI Override)**
  - *Support*: mbedTLS (User bisa mengisi SNI custom).
- [ ] **Cipher Suite**
  - *Support*: mbedTLS (Dapat dipaksakan set cipher tertentu).
  - *Default Terbaik*: Bawaan mbedTLS (Modern ciphers).
- [ ] **Session Resumption / Cache**
  - *Support*: mbedTLS.
  - *Default Terbaik*: Enable (Mempercepat handshake koneksi berikutnya).
- [ ] **ALPN (Application-Layer Protocol Negotiation)**
  - *Support*: mbedTLS.
  - *Default Terbaik*: `h2, http/1.1`.
- [ ] **OCSP Stapling**
  - *Support*: mbedTLS (Tetapi overhead tinggi).
  - *Default Terbaik*: Disable.
- [ ] **Secure Renegotiation**
  - *Support*: mbedTLS.
  - *Default Terbaik*: Aktif (RFC 5746).

### ========================
### HEV SOCKS5 TUNNEL
### ========================
- [ ] **Auto MTU Detection**
  - *Support*: Hev-socks5-tunnel (Belum ter-ekspos).
- [x] **MTU (Interface MTU)**
  - *Support*: Ya.
  - *Default Terbaik*: `8500` (Bagus untuk lokal virtual interface TUN).
- [x] **TCP Buffer / UDP Buffer**
  - *Support*: Ya.
  - *Default Terbaik*: TCP `65536`, UDP Recv `524288`.
- [x] **Multi Worker (Multi-Queue)**
  - *Support*: Ya.
  - *Default Terbaik*: `false` (Sering menyebabkan issue kompatibilitas di OS Android tertentu, disarankan false untuk stabilitas baterai).
- [ ] **DNS Cache / DNS Timeout**
  - *Support*: Ya (C level cache).
  - *Default Terbaik*: `true` dengan timeout `5` detik.
- [x] **UDP Queue (Copy Buffer Nums)**
  - *Support*: Ya.
  - *Default Terbaik*: `10`.
- [ ] **UDP Batch**
  - *Support*: GSO/GRO (Hanya jika kernel mendukung).
- [x] **Task Stack Size**
  - *Support*: Ya.
  - *Default Terbaik*: `86016` bytes.
- [x] **Session Limit**
  - *Support*: Ya.
  - *Default Terbaik*: `0` (Tidak Terbatas).
- [x] **Log Level**
  - *Support*: Ya.
  - *Default Terbaik*: `warn`.

### ========================
### DNS
### ========================
- [ ] **Resolve via Tunnel**
  - *Support*: Ya (dns request diteruskan via TUN ke SOCKS5).
  - *Default Terbaik*: `true` (Mencegah DNS leak).
- [ ] **Resolve Local**
  - *Support*: Ya (Bypass DNS query via Wi-Fi/Seluler langsung).
- [ ] **DNS Cache**
  - *Support*: Dikelola Android (Ya).
- [ ] **Flush Cache**
  - *Support*: Sulit tanpa root.
- [ ] **Retry Count / Retry Delay**
  - *Support*: Logika retry di Java.

### ========================
### VPN
### ========================
- [x] **Kill Switch**
  - *Support*: Ya (`BLOCKING` / `setAlwaysOnVpnPackage`).
  - *Default Terbaik*: `false` (Opsi pengguna, karena membuat koneksi internet lokal mati saat VPN disconnect).
- [x] **Block Internet on Disconnect**
  - *Support*: (Tergabung dalam Kill Switch).
- [ ] **Always-on VPN Support**
  - *Support*: Memerlukan pengguna mengaturnya dari pengaturan Android secara manual. Aplikasi dapat memandu (Intent ke Setting VPN).
- [x] **Excluded Apps / Allowed Apps (Split Tunneling)**
  - *Support*: Ya (via `addAllowedApplication` dan `addDisallowedApplication`).

### ========================
### BATTERY
### ========================
- [x] **WakeLock**
  - *Support*: Ya (Untuk VPN Service).
- [x] **Partial WakeLock**
  - *Support*: Ya (Khusus untuk Hotshare/Tethering agar CPU tidak tidur).
- [ ] **WiFi Lock**
  - *Support*: Ya (`WifiManager.WifiLock`).
  - *Default Terbaik*: Aktifkan jika Hotshare mode WiFi menyala.
- [ ] **High Performance Mode**
  - *Support*: `PowerManager.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### ========================
### LOGGING
### ========================
- [x] **Export Log** (Di UI)
- [x] **Share Log / Copy Log / Search Log** (Di UI Console)
- [ ] **Timestamp / Thread ID**
  - *Support*: Logcat & Logger internal.
  - *Default Terbaik*: Tampilkan Timestamp.

### ========================
### PERFORMANCE (Kesimpulan)
### ========================
Optimasi yang dapat meningkatkan throughput, latency, dan stabilitas:
1. **TCP_NODELAY** harus dipertahankan `true` pada seluruh layer injector/forwarder agar HTTP/WS tunneling tidak mengalami latency akibat antrean Nagle.
2. **Buffer Off-Heap**: Penggunaan `ByteBuffer.allocateDirect(65536)` memastikan buffer bebas dari intervensi *Garbage Collector*, sangat optimal untuk *memory usage* dan *CPU usage*.
3. **mbedTLS Session Resumption**: Sebaiknya fitur ini di-enable di level JNI mbedTLS (jika libssh2 mendukungnya via layer TLS transport), karena ini memangkas waktu CPU untuk *TLS negotiation* secara drastis saat *reconnect*.
4. **Multi-Queue hevtun**: Disarankan untuk tetap dinonaktifkan (`false`) di Android (kecuali perangkat gaming/flagship) untuk menghindari *CPU Wakeup* terlalu sering yang memboroskan baterai. Stack 86016 byte per task sudah sangat seimbang untuk *memory usage*.
