# SI VPN — Panduan Penggunaan & Konfigurasi Lengkap

Selamat datang di panduan resmi penggunaan dan konfigurasi **SI VPN**, aplikasi VPN tunnel berbasis SSH (Secure Shell) berkinerja tinggi yang dirancang khusus untuk Android menggunakan arsitektur modular Jetpack Compose dan integrasi Native JNI (Java Native Interface).

Dokumen ini ditujukan bagi pengguna akhir maupun administrator jaringan yang ingin memaksimalkan potensi aplikasi ini untuk keperluan keamanan, enkripsi data, pembatasan sensor internet, serta berbagi koneksi internet (*sharing proxy*).

---

## Daftar Isi
1. [Langkah Awal: Pemasangan Core JNI Driver](#langkah-awal-pemasangan-core-jni-driver)
2. [Konfigurasi Akun SSH (SSH Settings)](#1-konfigurasi-akun-ssh-ssh-settings)
3. [Konfigurasi Payload & HTTP Header (Payload Configuration)](#2-konfigurasi-payload-http-header-payload-configuration)
4. [Konfigurasi Proxy Koneksi (Proxy Connection Config)](#3-konfigurasi-proxy-koneksi-proxy-connection-config)
5. [Konfigurasi Server Name Indication (SNI / SSL / TLS)](#4-konfigurasi-server-name-indication-sni--ssl--tls)
6. [Fitur Auto Reconnect](#5-fitur-auto-reconnect)
7. [Pengaturan DNS Sektor Aman (DNS Settings)](#6-pengaturan-dns-sektor-aman-dns-settings)
8. [Pengaturan UDP Gateway untuk Gaming & Voip (UDPGW Settings)](#7-pengaturan-udp-gateway-untuk-gaming--voip-udpgw-settings)
9. [Berbagi Koneksi VPN via Hotspot (Fitur Hotshare)](#8-berbagi-koneksi-vpn-via-hotspot-fitur-hotshare)
10. [Membaca Konsol Log & Troubleshooting](#9-membaca-konsol-log--troubleshooting)

---

## Langkah Awal: Pemasangan Core JNI Driver

Untuk menjaga ukuran distribusi aplikasi tetap ringkas dan mematuhi kebijakan optimasi arsitektur perangkat, **SI VPN** menggunakan mekanisme *Dynamic JNI OTA (Over-The-Air) Binary Loading*. Inti pemrosesan paket jaringan berjalan di atas kode mesin C++ native (melalui file biner `libssh.so` untuk enkripsi SSH dan `libhev-socks5-tunnel.so` untuk perutean transparan virtual TUN).

Sebelum menekan tombol hubungkan untuk pertama kali:
1. Pastikan perangkat Anda terhubung ke internet.
2. Cari panel **Unduh JNI Online** di bagian bawah antarmuka utama.
3. Aplikasi secara otomatis mendeteksi arsitektur CPU Anda (misalnya `arm64-v8a` atau `armeabi-v7a`).
4. Ketuk tombol **Unduh Pustaka Native**. Aplikasi akan mengambil berkas langsung dari server repositori dan menyimpannya ke dalam direktori sandbox internal aplikasi secara aman.
5. Indikator status di UI akan berubah dari warna merah (Pustaka Belum Siap) menjadi warna hijau stabil yang menandakan driver JNI siap digunakan.

---

## 1. Konfigurasi Akun SSH (SSH Settings)

SSH bertindak sebagai saluran enkripsi utama untuk membungkus semua lalu lintas internet Anda melewati enkripsi ujung-ke-ujung (*end-to-end encryption*).

**Langkah-langkah Pengaturan:**
- **Server IP / Host**: Masukkan alamat IP atau hostname server SSH Anda (misalnya `sg-server1.ssh.net`).
- **Server Port**: Port default SSH biasanya adalah `22`, namun jika server Anda menggunakan port alternatif (misalnya untuk bypass firewall atau port SSL Dropbear), sesuaikan nilainya (contoh: `143`, `443`, `80`).
- **Username**: Nama pengguna akun SSH Anda.
- **Password**: Kata sandi authentikasi akun SSH Anda.

---

## 2. Konfigurasi Payload & HTTP Header (Payload Configuration)

Fitur Payload digunakan untuk memanipulasi header HTTP permintaan sebelum dikirimkan ke server proxy atau server SSH. Teknik ini sangat berguna saat Anda harus melewati gerbang sensor ISP (Internet Service Provider) tertentu yang menerapkan kuota/tarif nol pada situs tertentu (*zero-rating bug*).

### Format Penulisan Simbolis
Gunakan simbol kontrol berikut dalam penulisan payload:
- `[host_port]`: Menggantikan target server SSH (`host:port`).
- `[host]`: Menggantikan target IP/Host SSH saja.
- `[port]`: Menggantikan port target SSH saja.
- `\r`: Untuk simbol carriage return (CR).
- `\n`: Untuk simbol line feed (LF).

### Contoh Struktur Payload Umum
1. **Payload Standard HTTP Proxy Injector:**
   ```http
   CONNECT [host_port] [protocol]\r\nHost: bug-operator-kamu.com\r\nConnection: Keep-Alive\r\n\r\n
   ```
2. **Payload WebSocket SSH (Cloudflare / CDN):**
   ```http
   GET / HTTP/1.1\r\nHost: bug.operator.com\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n
   ```

---

## 3. Konfigurasi Proxy Koneksi (Proxy Connection Config)

Jika koneksi SSH Anda tidak berjalan langsung (*Direct connection*) melainkan harus transit melalui proxy perantara (misalnya APN Proxy bawaan operator seluler atau Squid Proxy server Anda), gunakan kartu pengaturan ini.

- **Proxy Host**: Masukkan alamat IP atau hostname proxy server lokal/operator (contoh: `10.1.89.150` atau `squid.my-proxy.net`).
- **Proxy Port**: Masukkan port proxy (biasanya `80`, `8080`, `3128`).
- **Otentikasi Proxy (Opsional)**: Jika proxy Anda membutuhkan kredensial keamanan, nyalakan opsi otentikasi lalu masukkan username dan password proxy yang sesuai.

---

## 4. Konfigurasi Server Name Indication (SNI / SSL / TLS)

SNI (Server Name Indication) adalah metode perutean enkripsi SSL/TLS tingkat tinggi. Fitur ini memaksa pengiriman header handshake TLS awal mendarat pada nama server internet yang dianggap bebas tarif oleh penyedia layanan data Anda (sering kali disebut sebagai *SSL bug host*).

- **SNI Host**: Masukkan nama server palsu yang telah terdaftar bebas filter oleh ISP Anda (misalnya: `m.youtube.com`, `line.me`, atau domain pembelajaran online bebas kuota).
- **Mode Enkripsi**: Ketika kolom ini terisi, sistem secara otomatis membungkus lalu lintas SSH di dalam enkripsi SSL handshake menggunakan target SNI yang dimasukkan.

---

## 5. Fitur Auto Reconnect

Untuk menjaga keandalan jaringan jika terjadi fluktuasi sinyal radio seluler atau penggantian BTS, sistem dilengkapi dengan mesin auto-reconnect proaktif.

- **Status Pemicu**: Aktifkan sakelar penemuan status jaringan.
- **Delay Waktu**: Tentukan jeda detik (biasanya 3–5 detik) sebelum mesin JNI melakukan percobaan ulang (*retry*) untuk membangun ulang handshake SSH jika terputus tiba-tiba.

---

## 6. Pengaturan DNS Sektor Aman (DNS Settings)

Jika Anda ingin menghindari sensor DNS lokal (*DNS hijacking* atau sensor nawala), atau ingin meningkatkan performa resolusi nama alamat situs, Anda dapat mengaktifkan DNS kustom.

- **DNS Primer / Sekunder**: Secara bawaan, aplikasi akan mengarahkan semua kueri nama domain pengguna ke layanan publik berkinerja tinggi, aman, dan privasi penuh seperti:
  - DNS Cloudflare: `1.1.1.1` & `1.0.0.1`
  - DNS Google: `8.8.8.8` & `8.8.4.4`
- Anda dapat mengubah kolom input langsung di UI aplikasi untuk menggunakan server DNS privat Anda sendiri.

---

## 7. Pengaturan UDP Gateway untuk Gaming & VOIP (UDPGW Settings)

Layanan SSH pada dasarnya hanya mendukung transmisi paket TCP saja. Tanpa adanya sistem penterjemah paket, aplikasi seperti game online multi-pemain (Mobile Legends, PUBG) atau panggilan suara (WhatsApp Call, Discord) tidak dapat berfungsi melewati jalur VPN.

Untuk itulah aplikasi ini menyediakan gerbang perutean UDPGW (UDP Gateway):
- **UDPGW Address**: Tentukan alamat peladen BadVpn UDP Gateway (format default lokal: `127.0.0.1:7300`).
- **Cara Kerja**: Aplikasi menterjemahkan seluruh paket protokol UDP perangkat dan melewatkannya ke dalam port perantara, lalu mengenkripsinya sebagai data stream TCP melewati pipa SSH dengan lancar tanpa hambatan, memberikan latensi rendah untuk sesi gaming.

---

## 8. Berbagi Koneksi VPN via Hotspot (Fitur Hotshare)

Salah satu fitur unggulan yang dirancang dengan presisi pada aplikasi ini adalah **Hotshare**. Melalui fitur ini, Anda dapat membagikan koneksi internet terenskripsi VPN dari ponsel Android Anda ke laptop (Windows / macOS / Linux), konsol game, atau ponsel pintar lainnya langsung melalui jaringan Wi-Fi Hotspot atau kabel data USB Tethering.

Sistem operasi Android bawaan secara normal menyensor perutean paket VPN agar tidak merembes keluar melalui Wi-Fi sharing. Hotshare berhasil mengelabui aturan ini dengan menjalankan peladen proksi berkinerja tinggi di dalam memori internal latar belakang.

### Langkah-langkah Penggunaan Menggunakan Wi-Fi Hotspot:
1. Hubungkan koneksi VPN hingga status di layar Anda menunjukkan **CONNECTED**.
2. Aktifkan fitur **Hotspot Wi-Fi** portabel di pengaturan Android Anda.
3. Buka tab **Hotshare** pada aplikasi SI VPN, kemudian ketuk tombol **Mulai Hotshare**.
4. Aplikasi akan meluncurkan dua jenis server proxy internal secara bersamaan:
   - **Proxy SOCKS5** (Default Port: `1080`)
   - **Proxy HTTP** (Default Port: `8080`)
5. Hubungkan laptop atau perangkat target Anda ke jaringan Wi-Fi Hotspot ponsel Anda.
6. Pada perangkat target, buka pengaturan jaringan (Network and Internet Settings) lalu cari menu "Proxy Settings".
7. Konfigurasikan proxy secara manual pada perangkat target dengan detail berikut:
   - **Tipe Jaringan Hotspot Biasa:**
     - **Proxy IP Address**: `192.168.43.1` (IP hotspot default perangkat Android).
     - **Port HTTP**: `8080` (Atau port yang tertera pada panel Hotshare aplikasi Anda).
     - **Port SOCKS**: `1080` (Jika aplikasi target dapat menerima koneksi SOCKS secara langsung).
   - **Tipe Kabel USB Tethering:**
     - **Proxy IP Address**: `192.168.42.129` (IP default ketika berbagi koneksi internet menggunakan kabel data kawat tembaga).
     - **Port HTTP**: `8080`
     - **Port SOCKS**: `1080`

---

## 9. Membaca Konsol Log & Troubleshooting

Panel konsol log pada layar utama menyajikan visualisasi data yang terjadi secara real-time di bawah kap mesin aplikasi. Setiap log dikategorikan berdasarkan warna dan prefiks demi memudahkan analisis masalah:

- **Warna Merah / [ERROR]**: Terjadi kegagalan penempatan socket port, kesalahan struktur config, atau pembatalan sertifikasi VPN.
- **Warna Emas / [SSH]**: Pesan yang datang langsung dari pemrosesan kriptografi native `libssh.so`.
- **Warna Hijau / [VPN]**: Menunjukkan kondisi perutean file descriptor interface virtual TUN dan inisialisasi loopback local proxy.
- **Warna Hijsu / [HOTSHARE]**: Log seputar transfer byte data dari klien eksternal ke peladen lokal.

### Beberapa Masalah Umum & Solusinya:
1. **VPN Sulit Menulis / Gagal Membuat Interface Tun (`Establish Error`)**:
   - *Solusi*: Pastikan Anda telah memberikan persetujuan saat muncul dialog pop-up izin VPN Android (`VpnService.prepare`). Jika dialog tidak tampil, coba hidupkan dan matikan mode pesawat (*Airplane mode*) sebentar, lalu buka kembali aplikasi.
2. **Koneksi Terkunci di Status Handshake (`SSH Auth failed` / `Timeout` / `Port binding busy`)**:
   - *Solusi*: Periksa kembali apakah username, server IP, atau kata sandi Anda ditulis dengan benar tanpa adanya spasi tersembunyi. Pastikan port SSH yang Anda masukan terbuka penuh di pelataran firewall server target Anda.
3. **Klien Hotshare Terhubung tapi Tidak Ada Akses Internet**:
   - *Solusi*: Periksa kembali apakah pengaturan server proxy manual di sistem laptop atau peramban browser target Anda telah dimasukan dengan benar (`192.168.43.1:8080`). Seringkali aplikasi browser memerlukan persetujuan penggunaan proxy manual agar dapat mengalirkan lalu lintas data eksternal.
