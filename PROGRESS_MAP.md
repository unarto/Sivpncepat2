# PROGRESS MAP

## Completed Tasks
- [x] Buat aturan agen AI pada `AGENTS.md` (Restriksi JNI build, No Mock/Placeholders, Modular SRP) - [2026-07-22 01:35]
- [x] Membaca dan memahami panduan `HEV_TUNNEL_SSH_INTEGRATION_GUIDE.md` - [2026-07-22 01:39]
- [x] Refaktor seluruh direktori dan package dari `com.example` / `com.aistudio.sivpn` ke `com.sivpn.cepat` (serta applicationId & namespace) - [2026-07-22 02:19]
- [x] Membuat panduan AI Agent di `PANDUAN_AGEN_AI_PROYEK.md` - [2026-07-22 02:51]
- [x] Implementasi backend JNI Wrapper `hev-socks5-tunnel` di `TProxyService.kt` dan penghapusan package usang `hev.socks5` - [2026-07-22 03:12]
- [x] Analisis logika native C `libssh2+mbedtls` dan implementasi pemetaan return code error di JNI Wrapper `SiVpnService.kt` (Strict SRP & Fail-Fast) - [2026-07-22 04:15]
- [x] Analisis RFC & Source Code libssh2 terkait proxy response drop, serta perbaikan presisi logika tag payload injector `PayloadInjector.kt` - [2026-07-22 04:26]
- [x] Identifikasi file raksasa `MainActivity.kt` (4848 baris) untuk refaktorisasi UI dan State - [2026-07-22 04:30]
- [x] Buat panduan arsitektur refaktor di `PANDUAN_REFACTOR.md` - [2026-07-22 18:30]
- [x] Refaktor total `MainActivity.kt` (4848 baris -> 85 baris) menjadi modul terpisah MVVM + Clean Architecture - [2026-07-22 18:36]
- [x] Pindahkan `PANDUAN_REFACTOR.md` ke direktori root workspace - [2026-07-22 18:42]
- [x] Audit dan pemetaan ukuran berkas source code untuk deteksi file gemuk - [2026-07-22 18:44]
- [x] Hapus file `.github/workflows/build.yml` sesuai instruksi user - [2026-07-22 18:54]
- [x] Tambah alur kerja `.github/workflows/gemini.yml` untuk AI Code Review - [2026-07-22 18:57]
- [x] Refaktor total `SiVpnService.kt` (755 baris -> 366 baris) dengan memisahkan logika ke `VpnNotificationManager`, `VpnInterfaceConfigurator`, `VpnMonitors`, dan `HevTunnelConfigurator` - [2026-07-23 02:51]
- [x] Refaktor total `VpnSettingsManager.kt` (650 baris -> 133 baris) dengan memisahkan fungsi ekspor/impor JSON ke `ConfigRepository` dan memadatkan getters/setters. - [2026-07-23 02:56]
- [x] Refaktor total `PayloadInjector.kt` menjadi professional WebSocket Injector dengan native WS/WSS Framing RFC6455 dan state TCP yang solid. - [2026-07-23 10:31]
- [x] Refaktor `LocalPortForwarder.kt` untuk optimalisasi TCP, zero leak, pembatas koneksi, logging, dan graceful shutdown. - [2026-07-23 10:49]
- [x] Audit mendalam dan perbaikan bug race condition di `LocalPortForwarder.kt` (invokeOnCompletion koordinasi coroutine). - [2026-07-23 11:03]
- [x] Refaktor total `PayloadInjector.kt` dengan validasi RFC6455 ketat, optimasi WebSocket, error handling dan stabilitas TCP. - [2026-07-23 11:13]
- [x] Refaktor `HttpProxyServer.kt` untuk optimalisasi TCP, zero leak, pembatas koneksi, logging, dan graceful shutdown. - [2026-07-23 11:25]
- [x] Audit ulang ukuran file & pembuatan rencana refaktor terukur (RSP) - [2026-07-23 11:38]
- [x] Langkah 1: Refaktor `MainViewModel.kt` dan ekstraksi logika UI state dialog ke `DialogUiState.kt` & `DialogViewModel.kt`. - [2026-07-23 11:42]
- [x] Langkah 2: Memecah komponen UI `MainScreen.kt` dan mengekstrak `MainScreenDialogs.kt` agar lebih bersih. - [2026-07-23 11:44]
- [x] Langkah 3: Ekstraksi logika framing websocket ke `WebSocketFramer.kt` dan string parsing payload ke `PayloadFormatter.kt` dari file `PayloadInjector.kt`. - [2026-07-23 11:46]
- [x] Memperbarui `Theme.kt` dan `Color.kt` menggunakan skema palet VPN modern (Deep Teal / Blue) dengan dukungan kompatibilitas Material 3 *dynamic color* - [2026-07-23 12:00]
- [x] Menyempurnakan `VpnSettingsManager.kt` dengan internal string keys object, optimalisasi thread-safety MMKV, dan validasi nilai numerik secara komprehensif. - [2026-07-23 12:10]
- [x] Melakukan Audit Fitur Jaringan Tingkat Lanjut (Advanced Networking Feature Audit) pada seluruh stack (`libssh2`, `mbedTLS`, `hev-socks5-tunnel`, `Android VpnService`) dan menambahkannya ke `AUDIT.md`. - [2026-07-23 13:50]
- [x] Memperbarui konfigurasi `.github/workflows/gemini.yml` untuk penyesuaian model Gemini (`gemini-2.5-flash`). - [2026-07-23 16:20]
- [x] Refaktor dan penyempurnaan `PayloadFormatter.kt` dengan dukungan tag lengkap (case-insensitive, user-agent, netData, raw, split) dan penggantian substring aman. - [2026-07-23 17:30]
- [x] Audit dan refactor lanjutan `PayloadFormatter.kt` (Android API 24+ android.util.Base64, validasi Host strict require(), format IPv6 RFC [ip]:port, escape sequences \r\n \n\r \t \\, filtering random/rotate, Math.floorMod, SplitType metadata, thread-safe, single-pass algorithm & KotlinDoc) - [2026-07-23 17:55]
- [x] Perbaikan Bug IPv6 host:port formatting & Eliminasi PayloadChunk kosong pada `PayloadFormatter.kt` - [2026-07-23 18:02]
- [x] Perbaikan Bug `PayloadInjector.kt`: Pelacakan `activeSockets` & eliminasi race condition `stop()`, penghilangan kebocoran resource/channel pada `forwardStream()`, perbaikan koordinasi `invokeOnCompletion` penutup socket untuk mencegah deadlock `joinAll()`, penyaringan ketat `SSLException`/`SSLHandshakeException` pada loop retry, serta penguraian `readWsHandshakeResponse` dengan toleransi CRLF/LF. - [2026-07-23 18:28]
- [x] Refaktor Arsitektur Koneksi Modular: Pemisahan `PayloadInjector.kt` (Orchestrator) ke dalam modul engine SRP (`SslEngine.kt`, `HttpPayloadEngine.kt`, `WebSocketEngine.kt`, `SquidProxyEngine.kt`, dan `StreamForwarder.kt`). - [2026-07-23 19:12]
- [x] Refaktor & Penyempurnaan `HttpPayloadEngine.kt`: Parser response HTTP, validasi status HTTP (200, 101, 301, 302, 307, 400, 403, 404, 407, 500, 502, 503, 504), pembuat otomatis CONNECT request, dukungan Proxy-Authorization Basic, logging HTTP, dan pemisahan fungsi modular (`buildPayload`, `sendPayload`, `readResponse`, `parseResponse`, `validateResponse`). - [2026-07-23 19:37]
- [x] Konfigurasi Alur Kerja GitHub Actions: Menghapus trigger otomatis (`push` & `pull_request`) di seluruh file workflow (`build-bebas-merdeka.yml`, `buildlissh.yml`, `gemini.yml`), sehingga eksekusi workflow HANYA berjalan secara manual via `workflow_dispatch` untuk menghemat kuota runner. - [2026-07-23 19:41]

## Pending Queue
- [ ] Fix bug parsing IPv6 host/port pada `SshParser.kt` & `ProxyParser.kt` (mencegah pemotongan string IPv6 berformat `[ip]:port`).
- [ ] Fix logika retry pendaftaran `VpnInterface` pada `SiVpnService.kt` jika `setupVpnInterface()` melempar Exception/SecurityException saat rekoneksi.
- [ ] Fix handling response code HTTP != 200 pada `PublicIpMonitor.kt` saat fetch IP publik (mencegah `FileNotFoundException` unhandled).
- [ ] Tambahkan sanitasi input `trim()` pada string dialog input (`SshDialog.kt`, `ProxyDialog.kt`, `PayloadDialog.kt`) sebelum disimpan ke repository.
