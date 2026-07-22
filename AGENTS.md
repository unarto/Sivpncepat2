# AGENT RULES & SYSTEM DIRECTIVES

## 1. BUILD & JNI CONFIGURATION RESTRICTIONS
- **DILARANG SENTUH** pengaturan build dan kompilasi JNI untuk komponen berikut:
  - `hev-socks5-tunnel`
  - `libssh2`
  - `mbedtls`
  - `CMakeLists.txt` atau skrip build NDK terkait.
- Jangan menjalankan perintah kompilasi/build (`compile_applet`) di lingkungan AI Studio.

## 2. STRICT CODE INTEGRITY (NO MOCKS / PLACEHOLDERS / FAKE BINARIES)
- **DILARANG KERAS** menggunakan data hardcoded, simulasi, placeholder mock, `// TODO`, fake response, atau file biner palsu.
- Seluruh kode harus berupa logika produksi asli agar bug dan error dapat terdeteksi dengan jelas secara fail-fast.

## 3. SINGLE RESPONSIBILITY PRINCIPLE (RSP / SRP)
- Wajib menerapkan modul tanggung jawab tunggal (Single Responsibility Principle) secara ketat.
- Setiap modul/kelas harus terisolasi, khusus memegang satu fungsi/tanggung jawab tanpa memicu *directory/file bloat*.

## 4. EXECUTION CONTROL
- Selalu menunggu instruksi eksplisit dari user sebelum melakukan modifikasi kode atau langkah eksekusi berikutnya.
