# EcoLoop ♻️🌐
> **Platform Distribusi & Sirkularitas Material Sisa Non-B3 (Pilot DKI Jakarta)**  
> *Final Project — Mobile Technology Solution (MTS)*

[![Platform](https://img.shields.io/badge/Platform-Android%20Kotlin-brightgreen?logo=android)](https://developer.android.com/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-blue?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Backend](https://img.shields.io/badge/Backend-Ktor%20REST%20API-orange?logo=kotlin)](https://ktor.io/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%2F%20H2%20Mode-blue?logo=postgresql)](https://www.postgresql.org/)
[![SDG Target](https://img.shields.io/badge/SDG%2012-Target%2012.5%20(Reuse%20%26%20Recycle)-green?logo=unitednations)](https://sdgs.un.org/goals/goal12)

---

## 📖 1. Ringkasan Produk

**EcoLoop** adalah solusi teknologi bergerak (*mobile technology*) berbasis Android yang menghubungkan pemilik material sisa industri non-B3 (penjahit konveksi, toko ritel, UMKM pengemasan) dengan penerima yang dapat memanfaatkannya kembali (pengrajin kriya, seniman daur ulang, komunitas sirkular, dan ecobrick maker).

### 🎯 Nilai & Batasan MVP (Pilot DKI Jakarta)
* **Wilayah Operasi:** DKI Jakarta (mencakup 5 Wilayah Kota Administrasi: Jakarta Selatan, Jakarta Pusat, Jakarta Barat, Jakarta Timur, dan Jakarta Utara).
* **4 Kategori Material Non-B3:**
  1. 🧵 **Kain Perca:** Sisa potongan kain katun, denim, rayon, perca jahit.
  2. 📦 **Kardus Bersih:** Karton packing single/double wall kering & kokoh.
  3. 🍾 **Botol Plastik:** Botol PET bening bekas konsumsi yang sudah dicuci bersih.
  4. 🪵 **Potongan Kayu:** Sisa palet kayu pinus/jati belanda tanpa paku.
* **Model Distribusi:** Penyaluran gratis (donasi/kolaborasi), pengambilan mandiri (*self-pickup*), tanpa ketergantungan kurir maupun payment gateway.
* **Satuan Inventaris:** Kilogram (kg) dengan presisi desimal hingga 2 digit (`NUMERIC(10,2)`).
* **Dampak Berkelanjutan:** Mendukung **SDG 12 (Konsumsi dan Produksi yang Bertanggung Jawab)**, khususnya **Target 12.5**: secara substansial mengurangi timbulan limbah melalui pencegahan, pengurangan, daur ulang, dan penggunaan kembali.

---

## 🔄 2. Alur Transaksi & State Machine

```
   [Supplier Post Listing]
             │
             ▼
   [Receiver Browse & Filter]
             │
             ▼
   [Ajukan Reservasi (kg)] ──► Status: REQUESTED (Stok belum terpotong)
             │
             ├───────────────► Supplier REJECT / Receiver CANCEL
             ▼
   [Supplier APPROVE]      ──► Status: APPROVED (Stok available_kg terpotong atomik)
             │
             ├───────────────► Batal setelah disetujui (Stok di-rollback otomatis)
             ▼
   [Penerima Request QR]   ──► Server generate Ephemeral Token (SHA-256 Hash, TTL 15m)
             │
             ▼
   [Penyedia Pindai QR &]  ──► Verifikasi Token Sekali Pakai (Single-use)
   [Input Berat Aktual]    ──► Catat actual_kg ke tabel transfers
             │
             ▼
   [Transaksi SELESAI]     ──► Status: COMPLETED (Masuk ke Dashboard Dampak)
```

### 🛡️ Aturan Bisnis Kritis (Zero-Bug Policy):
1. **Pencegahan Double-Booking:** Pengurangan kuota stok saat `approve` dilakukan secara transaksional dengan pemeriksaan ketat `available_kg >= requested_kg`.
2. **Rollback Stok Otomatis:** Jika reservasi berstatus `APPROVED` dibatalkan (`cancel`), kuota `reserved_kg` dikembalikan secara utuh ke `available_kg`.
3. **Verifikasi Serah Terima QR:** Token QR dibuat acak, berumur pendek (15 menit), disimpan dalam bentuk hash SHA-256 di database, dan diverifikasi *single-use* saat diselesaikan.
4. **Metrik Dampak Sah:** Angka total kilogram pada dashboard dampak **hanya** dihitung dari catatan transaksi berstatus `COMPLETED` berdasarkan berat aktual saat serah terima, bukan angka estimasi awal.

---

## 🏛️ 3. Arsitektur Proyek

Proyek ini terstruktur ke dalam dua direktori independen:

```
Finpro MTS LEC/
├── backend/                       # Ktor REST API Service (Kotlin)
│   ├── src/main/kotlin/com/ecoloop/
│   │   ├── Application.kt         # Server Entry Point (Netty)
│   │   ├── plugins/               # HTTP, Auth JWT, CORS, StatusPages
│   │   ├── routes/                # REST Routing (/auth, /listings, /reservations, dll.)
│   │   ├── services/              # Business Logic & Strict Validation
│   │   ├── repositories/          # Exposed ORM Database Layer
│   │   └── dtos/                  # Serializable Request/Response Contracts
│   ├── src/main/resources/
│   │   └── db/migration/          # Flyway SQL Migration Scripts (V1)
│   ├── build.gradle.kts           # Gradle Ktor Configurations
│   └── gradlew.bat
│
├── frontend/                      # Android Native Application (Kotlin)
│   ├── app/src/main/
│   │   ├── java/com/ecoloop/app/
│   │   │   ├── MainActivity.kt    # Single Activity, App Scaffold, Navigation
│   │   │   └── Screens.kt         # Jetpack Compose Screens (Material 3)
│   │   └── AndroidManifest.xml    # Permissions (Camera, Internet, Cleartext)
│   ├── app/build.gradle.kts       # Android Compose, Retrofit, CameraX, ZXing
│   ├── build.gradle.kts
│   └── gradlew.bat
│
├── .gitignore                     # Filter rahasia, binary, database & file lokal
└── README.md
```

---

## 🚀 4. Panduan Menjalankan Proyek (Local Development)

### Prasyarat Lingkungan:
* **JDK:** Versi 17 atau 21 LTS (disarankan).
* **Android Studio:** Versi Ladybug / Koala atau yang lebih baru dengan Android SDK API 34/35.
* **Terminal / PowerShell.**

---

### A. Menjalankan Backend (Ktor API)

Backend telah dilengkapi database **H2 tersemat dalam mode kompatibilitas PostgreSQL**, sehingga dapat langsung dijalankan tanpa perlu menginstal atau menyalakan PostgreSQL secara manual.

1. Buka terminal dan masuk ke folder `backend`:
   ```bash
   cd backend
   .\gradlew.bat run
   ```
2. Tunggu hingga muncul log:
   ```
   INFO  io.ktor.server.Application - Responding at http://0.0.0.0:8080
   ```
3. API siap digunakan di `http://localhost:8080/api/v1/`.

*(Jika ingin menggunakan PostgreSQL eksternal/Docker, cukup set environment variable `DB_URL`, `DB_USER`, dan `DB_PASSWORD` sebelum menjalankan `.\gradlew.bat run`).*

---

### B. Menjalankan Frontend (Android Studio)

1. Buka **Android Studio**.
2. Pilih menu **File > Open...**, lalu arahkan ke folder:  
   `Finpro MTS LEC/frontend`
3. Tunggu hingga proses **Gradle Sync** selesai di status bar bawah.
4. Pilih target perangkat:
   * **Emulator:** Gunakan **Pixel 9 Pro (API 35)** untuk kestabilan grafis terbaik.
   * **HP Android Fisik:** Hubungkan HP via kabel USB dengan opsi **USB Debugging** aktif.
5. Klik tombol hijau **Run ▶** (atau tekan `Shift + F10`).
6. Aplikasi **EcoLoop** akan langsung terpasang dan berjalan di perangkat.

> **Catatan Jaringan Real Device:**  
> Jika menguji menggunakan HP fisik via Wi-Fi, pastikan HP dan Laptop berada di jaringan Wi-Fi yang sama, dan `BASE_URL` di `app/build.gradle.kts` disetel mengarah ke alamat IPv4 Wi-Fi laptop Anda (misal `http://192.168.10.101:8080/api/v1/`).

---

## 📡 5. Ringkasan Endpoint REST API (v1)

Semua respons sukses dibungkus dalam format standar `{"data": ...}` dan error dalam format `{"error": {"code": "...", "message": "..."}}`.

| Method | Endpoint | Akses | Keterangan |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Publik | Registrasi akun baru (DKI Jakarta) |
| `POST` | `/api/v1/auth/login` | Publik | Login & memperoleh token Bearer JWT |
| `GET` | `/api/v1/me` | Otentikasi | Mengambil profil pengguna yang sedang login |
| `PATCH` | `/api/v1/me` | Otentikasi | Memperbarui profil pengguna |
| `GET` | `/api/v1/categories` | Publik | Daftar 4 kategori material sirkular |
| `GET` | `/api/v1/listings` | Publik | Katalog material (filter `q`, `category`, `district`) |
| `POST` | `/api/v1/listings` | Otentikasi | Penyedia memposting listing material baru |
| `POST` | `/api/v1/reservations` | Otentikasi | Penerima mengajukan reservasi material (kg) |
| `GET` | `/api/v1/reservations` | Otentikasi | Riwayat reservasi (`?view=incoming` atau `outgoing`) |
| `POST` | `/api/v1/reservations/{id}/approve` | Otentikasi | Penyedia menyetujui reservasi & mengunci stok |
| `POST` | `/api/v1/reservations/{id}/reject` | Otentikasi | Penyedia menolak permintaan reservasi |
| `POST` | `/api/v1/reservations/{id}/cancel` | Otentikasi | Pembatalan reservasi & pengembalian kuota stok |
| `POST` | `/api/v1/reservations/{id}/handover-token` | Otentikasi | Penerima membuat token QR sekali pakai |
| `POST` | `/api/v1/reservations/{id}/complete` | Otentikasi | Penyedia menyelesaikan serah terima fisik |
| `GET` | `/api/v1/impact/me` | Otentikasi | Statistik kg tersalurkan & metrik SDG 12.5 |

---

## 🔐 6. Keamanan & Kebersihan Repositori (Git Security)

File `.gitignore` di repositori ini telah dikonfigurasi secara ketat untuk mencegah kebocoran data sensitif ke publik:
* ❌ **File Rahasia:** File `.env`, credential, dan keystore signing tidak dilacak Git.
* ❌ **Database Lokal:** File database runtime (`data/`, `*.mv.db`) diabaikan agar data akun lokal tidak ikut terunggah.
* ❌ **Konfigurasi Lokal:** File `local.properties` (berisi path lokal SDK dan username PC) diabaikan.
* ❌ **Build Binaries:** Folder `.gradle/`, `.kotlin/`, `build/`, serta file paket `.apk` dan `.aab` dikecualikan dari repositori untuk menjaga ukuran repo tetap ramping.

---

## 👥 7. Tim Pengembang & Kontribusi

Proyek ini dikembangkan oleh **Tim 5 — Mobile Technology Solution (MTS LEC)**:
* **Captain Nayaka** & Tim Engineering EcoLoop.
* **Arsitektur:** Clean Architecture, Unidirectional Data Flow, Zero-Trust Security Policy.

---
*EcoLoop — Memberi Bahan Sisa Kesempatan Kedua untuk Bumi yang Berkelanjutan.* 🌍🌱
