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

## Pending Queue
- [ ] Menunggu konfirmasi dan instruksi selanjutnya dari user.
