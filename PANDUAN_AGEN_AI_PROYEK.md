# AI AGENT DEVELOPMENT RULES

## Project Development Guidelines

Dokumen ini adalah aturan kerja untuk AI Agent yang membantu pengembangan project.

AI wajib memahami arsitektur project sebelum melakukan perubahan kode.

Prioritas utama:

1. Stabilitas
2. Kompatibilitas
3. Maintainability
4. Fitur baru


---

# 1. ATURAN UTAMA AI AGENT

AI DILARANG:

- Langsung mengubah kode besar tanpa analisa.
- Rewrite project tanpa memahami struktur existing.
- Menghapus kode hanya karena dianggap tidak digunakan.
- Mengganti nama class, package, function native tanpa pengecekan.
- Membuat implementasi baru yang bertentangan dengan arsitektur.


Sebelum coding perubahan besar, AI wajib memberikan:

- Analisa masalah.
- Rencana perubahan.
- Daftar file yang akan diubah.
- Dampak perubahan.
- Risiko kompatibilitas.


---

# 2. ARSITEKTUR UTAMA PROJECT

Project menggunakan:

- Android VpnService
- hev-socks5-tunnel native engine
- JNI bridge
- SSH backend
- Custom Payload
- SOCKS5 Proxy


Flow utama:

```
Android VPN Service

        |

        |

TProxyService.kt

        |

        |

JNI Bridge

        |

        |

hev-socks5-tunnel.so

        |

        |

SOCKS5 Tunnel

        |

        |

SSH Socket / Custom Payload / Proxy Backend
```


---

# 3. ATURAN HEV-SOCKS5-TUNNEL

hev-socks5-tunnel adalah native engine.

AI TIDAK BOLEH:

- Menulis ulang core hev-socks5-tunnel.
- Mengubah algoritma native tanpa alasan.
- Mengganti struktur source native.
- Menghapus fungsi JNI.


Source native dianggap sebagai dependency engine.


Perubahan hanya diperbolehkan pada:

- JNI wrapper.
- Konfigurasi YAML.
- Lifecycle Android.
- Backend management.


---

# 4. ATURAN JNI BRIDGE

File JNI:

```
hev-jni.c
```


CLSNAME harus selalu sama dengan Kotlin JNI wrapper.


Contoh:

C:

```c
#define CLSNAME TProxyService
```


Maka Kotlin:

```kotlin
object TProxyService {

    @JvmStatic
    external fun TProxyStartService(
        configPath: String,
        fd: Int
    )

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    external fun TProxyGetStats(): LongArray?

}
```


AI WAJIB mengecek:

- Nama class.
- Package path.
- Nama native function.
- Nama library.


Kesalahan CLSNAME menyebabkan:

- UnsatisfiedLinkError
- Native method tidak ditemukan
- Force close aplikasi


---

# 5. ATURAN NAMA LIBRARY

Jika hasil build:

```
libhev-socks5-tunnel.so
```

Maka load:

```kotlin
System.loadLibrary("hev-socks5-tunnel")
```


Nama harus sama.

Jangan mengganti:

```
hev-socks5-tunnel
```

menjadi:

```
hevtun
```

atau nama lain tanpa perubahan build system yang jelas.


---

# 6. PEMBAGIAN TUGAS BACKEND


## TProxyService.kt

Tanggung jawab:

- JNI bridge.
- Load native library.
- Membuat config tunnel.
- Mengirim TUN file descriptor.
- Start tunnel.
- Stop tunnel.


Tidak bertanggung jawab untuk:

- SSH login.
- Payload HTTP.
- Proxy squid.


---

## SshSocket.kt

Tanggung jawab:

- Membuat koneksi SSH.
- Mengelola socket.
- Forward tunnel.
- Menjaga koneksi.


Tidak boleh:

- Memanggil native JNI langsung.


---

## PayloadCustom.kt

Tanggung jawab:

- Membuat custom payload.
- Mengatur request awal.
- Mengelola header payload.


Tidak boleh:

- Mengatur lifecycle VPN.


---

## ProxySquid.kt

Tanggung jawab:

- Konfigurasi proxy squid.
- Endpoint proxy.
- Komunikasi proxy.


Tidak boleh:

- Menggantikan fungsi hev tunnel.


---

# 7. PACKAGE DAN CLASS RULE

Jika package berubah:

Contoh:

Dari:

```
com.aistudio.sivpn.a1b2c3
```

Menjadi:

```
com.sivpn.cepat
```


AI wajib update:

- Kotlin package.
- Import.
- AndroidManifest.
- Gradle namespace.
- ApplicationId.
- Directory source.


Setelah rename lakukan pencarian:

```
com.example
com.aistudio
a1b2c3
```


Tidak boleh ada sisa package lama.


---

# 8. BUILD SYSTEM RULE

Perhatikan:

- Gradle Kotlin DSL.
- Android SDK.
- NDK version.
- ABI.


ABI wajib:

```
arm64-v8a
armeabi-v7a
x86
x86_64
```


Sebelum mengubah build:

AI wajib memahami:

- compile script.
- NDK environment.
- output library.


---

# 9. WORKFLOW IMPLEMENTASI

Urutan kerja wajib:


## Tahap 1

Bersihkan project:

- Package.
- Namespace.
- Struktur folder.


## Tahap 2

Pastikan APK dapat build.


## Tahap 3

Test:

- Library loading.
- JNI binding.


## Tahap 4

Implement:

- VPN service.
- TProxyService.
- Config generator.


## Tahap 5

Implement backend:

- SSH.
- Custom Payload.
- Proxy.


Jangan mengerjakan semua sekaligus.


---

# 10. ATURAN OUTPUT AI

Saat memberikan solusi:

AI wajib menjelaskan:

- Apa yang berubah.
- Kenapa berubah.
- File yang terkena dampak.
- Cara testing.


Jangan hanya memberikan kode tanpa penjelasan.


---

# 11. TARGET AKHIR PROJECT

Membangun aplikasi VPN Android dengan arsitektur:

```
VpnService

    |

TProxyService

    |

JNI

    |

hev-socks5-tunnel

    |

SOCKS5

    |

SSH + Custom Payload + Proxy Backend
```


Dengan tujuan:

- Stabil.
- Mudah dikembangkan.
- Tidak merusak native engine.
- Kompatibel untuk pengembangan jangka panjang.


END OF RULES
