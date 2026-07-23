# PANDUAN REFACTORING ARCHITECTURE - SIVPN CEPAT

Dokumen ini berisi panduan arsitektur, peta refaktorisasi, dan struktur modular lengkap untuk melakukan pemisahan total pada file `MainActivity.kt` (4849 baris) ke dalam pola **MVVM + Clean Architecture** tanpa merubah tampilan UI sedikit pun.

---

## 1. STRUKTUR DIREKTORI BARU (`com.sivpn.cepat`)

```text
com.sivpn.cepat/
├── MainActivity.kt                      (Cleaned up Activity <= 150 baris)
├── TProxyService.kt                     (Backend JNI transparent proxy)
├── model/
│   ├── MainUiState.kt                   (Data Class penampung seluruh UI State)
│   └── SpeedInfo.kt                     (Data Class metrik kecepatan Rx/Tx)
├── parser/
│   ├── SshParser.kt                     (Parser kredensial & server SSH)
│   └── ProxyParser.kt                   (Parser IP & Port Remote Proxy)
├── monitor/
│   ├── PublicIpMonitor.kt               (Monitor IP Publik via Flow: ipify, ifconfig, icanhazip)
│   ├── PingMonitor.kt                   (Monitor Latensi Ping via Flow)
│   └── SpeedMonitor.kt                  (Monitor TrafficStats Rx/Tx via Flow)
├── vpn/
│   ├── VpnController.kt                 (Pengendali siklus VpnService & Permission)
│   └── ... (Manager native & JNI bawaan)
├── repository/
│   ├── SettingsRepository.kt            (Satu-satunya gatekeeper VpnSettingsManager)
│   ├── LogRepository.kt                 (Satu-satunya gatekeeper LogManager)
│   └── ConfigRepository.kt              (Gatekeeper Impor/Ekspor JSON & Clipboard)
├── viewmodel/
│   ├── MainViewModel.kt                 (StateFlow & Coroutine Scope pengelola bisnis)
│   └── MainViewModelFactory.kt          (Factory DI Sederhana untuk ViewModel)
└── ui/
    ├── MainScreen.kt                    (Entrypoint Composable UI <= 300 baris)
    ├── theme/                           (Theme Compose bawaan)
    ├── components/
    │   ├── TopBar.kt                    (TopAppBar, status IP, & Dropdown Menu)
    │   ├── StatusCard.kt                (Card Status Koneksi, Timer, Ping, Speed)
    │   ├── ProfileCard.kt               (Card Pemilih Profil)
    │   ├── ConnectionCard.kt            (Card Saklar/Toggle Koneksi)
    │   ├── PayloadCard.kt               (Card Edit Payload / Bug Host)
    │   ├── ProxyCard.kt                 (Card Remote Squid Proxy)
    │   ├── SshCard.kt                   (Card Akun & Server SSH)
    │   ├── DnsCard.kt                   (Card DNS Custom Resolver)
    │   ├── SpeedCard.kt                 (Card Detail Meteran Kecepatan)
    │   ├── LogCard.kt                   (Card Terminal Log Singkat)
    │   ├── SettingsCard.kt              (Card Pengaturan HevSocks & Parameter Lanjutan)
    │   ├── ThemeCard.kt                 (Card Selektor Tema)
    │   ├── SplitTunnelingCard.kt        (Card Bypass Aplikasi)
    │   ├── KillSwitchCard.kt            (Card Fitur Keamanan Kill Switch)
    │   └── SectionHeader.kt             (Header Label Seksi UI)
    └── dialogs/
        ├── PayloadDialog.kt             (Modal Editor Payload)
        ├── ProfileDialog.kt             (Modal Pengelola Profil)
        ├── LogDialog.kt                 (Modal Fullscreen Terminal Logs)
        ├── DnsDialog.kt                 (Modal Pengaturan Custom DNS)
        ├── SplitTunnelDialog.kt         (Modal Selektor Aplikasi Bypass)
        ├── TimeLimitDialog.kt           (Modal Batas Waktu Koneksi)
        ├── SettingsDialog.kt            (Modal Pengaturan Lanjutan / Battery / KeepAlive)
        ├── TetherDialog.kt              (Modal Hotshare / Root Tethering)
        └── JniDownloaderDialog.kt       (Modal Unduh Library Native JNI)
```

---

## 2. PEMETAAN FILE & DITEMPATKAN DI LAYAR APA

| Nama File Baru | Layer | Tanggung Jawab & Fungsi Utama |
| :--- | :--- | :--- |
| `MainUiState.kt` | **Model** | Menampung seluruh variabel state UI (SSH, Payload, Proxy, DNS, Speed, Ping, Status, Visibilitas Dialog, Setting HevSocks). |
| `SpeedInfo.kt` | **Model** | Data class untuk format kecepatan `rxSpeedBytesSec` dan `txSpeedBytesSec`. |
| `SshParser.kt` | **Parser** | Mengurai string format `host:port@username:password` menjadi komponen terpisah & sebaliknya. |
| `ProxyParser.kt` | **Parser** | Mengurai string format `host:port` menjadi IP host dan Integer port. |
| `PublicIpMonitor.kt` | **Monitor** | Mengecek IP publik secara terisolasi via HTTP request multi-endpoint dan memunculkannya via `Flow<String>`. |
| `PingMonitor.kt` | **Monitor** | Melakukan loop ping berkala pada target SSH/Custom Ping via `Flow<Long>`. |
| `SpeedMonitor.kt` | **Monitor** | Memantau `TrafficStats.getUidRxBytes()` dan `getUidTxBytes()` via `Flow<SpeedInfo>`. |
| `VpnController.kt` | **VPN / Manager**| Menangani `Intent(SiVpnService)`, `VpnService.prepare()`, dan pemeriksaan izin notifikasi. |
| `SettingsRepository.kt` | **Repository** | Membungkus panggilan `VpnSettingsManager` agar UI/ViewModel tidak mengakses langsung storage. |
| `LogRepository.kt` | **Repository** | Membungkus `LogManager.logs` dan penambahan log sistem. |
| `ConfigRepository.kt` | **Repository** | Membungkus ekspor/impor konfigurasi `.sivpn` (JSON) dan Clipboard. |
| `MainViewModel.kt` | **ViewModel** | Menampung `StateFlow<MainUiState>`, mengelola `viewModelScope`, menghubungkan Monitor & Repository. |
| Composables Card (`StatusCard`, dll) | **UI Components** | Komponen UI murni tanpa logic, hanya menerima data dari `MainUiState` dan callback event. |
| Dialogs (`PayloadDialog`, dll) | **UI Dialogs** | Modal dialog terpisah murni presentation layer. |

---

## 3. FUNGSI-FUNGSI YANG DIPINDAHKAN DARI `MainActivity.kt`

1. **Fungsi Network & Public IP Check** (`fetchPublicIp`, logic loop `api.ipify.org`, `ifconfig.me`, `icanhazip`) -> Dipindahkan ke `PublicIpMonitor.kt`.
2. **Fungsi Ping Loop & Latency Measurement** (`PingUtility.measureLatency` loop) -> Dipindahkan ke `PingMonitor.kt`.
3. **Fungsi Speedometer & Traffic Monitoring** (`TrafficStats` calculation loop) -> Dipindahkan ke `SpeedMonitor.kt`.
4. **Fungsi SSH Credential Parsing** (`sshFullInput` splitting & validation) -> Dipindahkan ke `SshParser.kt`.
5. **Fungsi Remote Proxy Parsing** (`proxyFullInput` splitting) -> Dipindahkan ke `ProxyParser.kt`.
6. **Fungsi Import & Export Config** (`exportLauncher`, `importLauncher`, JSON parsing) -> Dipindahkan ke `ConfigRepository.kt` & `MainViewModel.kt`.
7. **Fungsi Management VpnService** (`prepareAndStartVpn`, `startVpnService`, `stopService`) -> Dipindahkan ke `VpnController.kt`.
8. **Seluruh Panggilan Direct `VpnSettingsManager`** -> Dipindahkan ke `SettingsRepository.kt`.
9. **Seluruh `remember { mutableStateOf(...) }` di Compose** -> Dipindahkan menjadi `MutableStateFlow` di `MainViewModel.kt` & `MainUiState.kt`.

---

## 4. PRINSIP INTEGRITAS & TIDAK MERUBAH UI

- 100% Tampilan visual, warna gradient, ukuran font, padding, margin, ikon, dan alur interaksi pengguna dijamin **SAMA PERSIS**.
- Tidak menggunakan Mock, Fake Data, atau Dummy responses.
- `MainActivity` disederhanakan murni menjadi container launcher & lifecycle.

EOF
