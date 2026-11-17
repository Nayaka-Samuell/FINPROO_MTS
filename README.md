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
│   ├── app/src/main/java/com/ecoloop/app/
│   │   ├── MainActivity.kt        # Single Activity → EcoLoopRoot
│   │   ├── EcoLoopApplication.kt  # Membuat AppContainer sekali per proses
│   │   ├── AppContainer.kt        # Dependency container sederhana (session, API, repository)
│   │   ├── data/
│   │   │   ├── remote/            # EcoApi (Retrofit), ApiClient (OkHttp + interceptor), dto/
│   │   │   ├── local/             # SessionStore: JWT di DataStore + cache token di memori
│   │   │   └── repository/        # Auth/Catalog/Reservation repository, pesan error, DataChanges
│   │   ├── ui/
│   │   │   ├── theme/             # Color, Type, Theme (Material 3, palet EcoLoop 2.0)
│   │   │   ├── components/        # EcoPrimaryButton, EcoField, EcoStatusBadge, EcoMaterialCard,
│   │   │   │                      # CategoryArt, EcoEmptyState, ErrorPanel, Skeleton, Messenger
│   │   │   ├── navigation/        # Routes + EcoNavHost (bottom bar, FAB, snackbar)
│   │   │   ├── EcoLoopRoot.kt     # Splash → Login → Aplikasi (berdasarkan status sesi)
│   │   │   ├── UiState.kt         # sealed UiState: Loading / Success / Error
│   │   │   └── Formatters.kt      # Format kg & tanggal, validasi berat
│   │   └── feature/               # Satu folder per fitur, masing-masing Screen + ViewModel
│   │       ├── auth/  home/  detail/  listing/
│   │       ├── activity/          # + scan/QrScanner.kt (CameraX + ML Kit), HandoverQrCode.kt
│   │       └── impact/  profile/
│   ├── app/build.gradle.kts       # Compose, Retrofit, DataStore, CameraX, ML Kit, ZXing
│   └── gradlew.bat
│
├── database/
│   ├── ecoloop_dump.sql           # pg_dump PostgreSQL berisi data contoh
│   └── seed.ps1                   # Script pengisi data contoh lewat REST API
├── docs/
│   └── Final_Report_EcoLoop.md    # Draft laporan akhir
├── .gitignore                     # Filter rahasia, binary, database & file lokal
└── README.md
```

---

## 🚀 4. Panduan Menjalankan Proyek (Local Development)

> **Anggota tim baru?** Baca dulu [`docs/PANDUAN_TIM.md`](docs/PANDUAN_TIM.md). Isinya: peta kode ("mau ubah X, buka file Y"), alur demo pakai akun contoh, cara menambah fitur, dan aturan kerja bareng pakai Git.

### Prasyarat Lingkungan:
* **JDK:** Versi 17 atau 21 LTS (disarankan).
* **Android Studio:** Versi Ladybug / Koala atau yang lebih baru dengan Android SDK API 34/35.
* **Terminal / PowerShell.**

---

### A. Menyiapkan PostgreSQL (Docker)

```powershell
docker run -d --name ecoloop-postgres `
  -e POSTGRES_DB=ecoloop -e POSTGRES_USER=ecoloop -e POSTGRES_PASSWORD=<password-lokal> `
  -p 5432:5432 postgres:16-alpine
```

Flyway membuat seluruh tabel otomatis saat backend pertama kali dijalankan. Untuk langsung memakai data contoh, import dump (lihat bagian C).

> Tanpa Docker? Lewati langkah ini. Jika `DB_URL` tidak diset, backend otomatis memakai **H2 file lokal (mode PostgreSQL)** di `backend/data/`.

---

### B. Menjalankan Backend (Ktor API)

```powershell
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/ecoloop"
$env:DB_USER = "ecoloop"
$env:DB_PASSWORD = "<password-lokal>"
$env:JWT_SECRET = "<string-acak-minimal-32-karakter>"   # opsional di development
.\gradlew.bat run
```

Tunggu log `Responding at http://127.0.0.1:8080`. API siap di `http://localhost:8080/api/v1/`.

> Tanpa `JWT_SECRET`, backend membuat secret acak setiap kali start, sehingga token lama menjadi tidak valid. Aplikasi Android menangani ini otomatis: respons **401** menghapus token lalu kembali ke layar login.

```powershell
.\gradlew.bat test      # pengujian HTTP, aturan bisnis, konkurensi (H2 in-memory)
```

---

### C. Import / Export Database Contoh

`database/ecoloop_dump.sql` berisi skema + data contoh: **2 akun**, **6 listing di 4 kategori**, **1 transaksi COMPLETED** (3,8 kg kain perca) dan 1 reservasi REQUESTED.

| Akun demo | Peran | Password |
| :--- | :--- | :--- |
| `sari@ecoloop.demo` | Penyedia, Jakarta Selatan | `password123` |
| `budi@ecoloop.demo` | Penerima, Jakarta Timur | `password123` |

**Import** (container `ecoloop-postgres` sudah berjalan; dump memakai `--clean --if-exists`, jadi tabel lama ditimpa):

```powershell
docker cp database/ecoloop_dump.sql ecoloop-postgres:/tmp/ecoloop_dump.sql
docker exec ecoloop-postgres psql -U ecoloop -d ecoloop -v ON_ERROR_STOP=1 -f /tmp/ecoloop_dump.sql
```

Import dengan `psql` dari dalam container (versi 16) agar cocok dengan versi `pg_dump` yang membuat file.

**Isi ulang data lewat API** (database kosong, backend berjalan):

```powershell
powershell -ExecutionPolicy Bypass -File database\seed.ps1
```

**Export ulang** setelah data berubah:

```powershell
docker exec ecoloop-postgres pg_dump -U ecoloop -d ecoloop --no-owner --no-privileges --clean --if-exists -f /tmp/ecoloop_dump.sql
docker cp ecoloop-postgres:/tmp/ecoloop_dump.sql database/ecoloop_dump.sql
```

---

### D. Menjalankan Frontend (Android)

1. Buka **Android Studio** → **File > Open...** → folder `Finpro MTS LEC/frontend`, tunggu **Gradle Sync**.
2. Jalankan backend (bagian B), lalu klik **Run ▶** di emulator.

`BASE_URL` default adalah **`http://10.0.2.2:8080/api/v1/`**. Alamat `10.0.2.2` di emulator Android mengarah ke `localhost` komputer.

**HP fisik** (HP dan laptop di Wi-Fi yang sama): override lewat properti Gradle, tanpa mengubah kode.

```powershell
cd frontend
.\gradlew.bat assembleDebug -PBASE_URL=http://192.168.1.10:8080/api/v1/
.\gradlew.bat installDebug  -PBASE_URL=http://192.168.1.10:8080/api/v1/
```

Di Android Studio, isi `BASE_URL=http://<IP-LAN>:8080/api/v1/` di `~/.gradle/gradle.properties` (jangan di file proyek). Pastikan firewall Windows mengizinkan port 8080.

**Uji alur QR:** login sebagai `budi@…` di satu perangkat → Aktivitas → Keluar → *Tampilkan QR*. Login sebagai `sari@…` di perangkat lain → Aktivitas → Masuk → *Scan QR*. Kalau kamera tidak tersedia (mis. emulator), pakai *Masukkan token manual*.

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
| `GET` | `/api/v1/listings` | Publik | Katalog ACTIVE (filter `q`, `categoryId`, `district`, `page`, `limit`) |
| `GET` | `/api/v1/listings/{id}` | Publik | Detail listing (termasuk `categoryName`, `supplierName`, `photos`) |
| `POST` | `/api/v1/listings` | Otentikasi | Penyedia memposting listing material baru |
| `PATCH` | `/api/v1/listings/{id}` | Otentikasi | Penyedia mengubah listing miliknya |
| `DELETE` | `/api/v1/listings/{id}` | Otentikasi | Soft delete (status INACTIVE) |
| `POST` | `/api/v1/reservations` | Otentikasi | Penerima mengajukan reservasi material (kg) |
| `GET` | `/api/v1/reservations` | Otentikasi | Riwayat reservasi (`?view=incoming` atau `outgoing`); transaksi selesai menyertakan `actualKg` & `completedAt` |
| `POST` | `/api/v1/reservations/{id}/approve` | Otentikasi | Penyedia menyetujui reservasi & mengunci stok |
| `POST` | `/api/v1/reservations/{id}/reject` | Otentikasi | Penyedia menolak permintaan reservasi |
| `POST` | `/api/v1/reservations/{id}/cancel` | Otentikasi | Pembatalan reservasi & pengembalian kuota stok |
| `POST` | `/api/v1/reservations/{id}/handover-token` | Otentikasi | Penerima membuat token QR sekali pakai |
| `POST` | `/api/v1/reservations/{id}/complete` | Otentikasi | Penyedia menyelesaikan serah terima fisik |
| `GET` | `/api/v1/impact/me` | Otentikasi | Statistik kg tersalurkan & metrik SDG 12.5 |
| `POST` | `/api/v1/transfers/{id}/feedback` | Otentikasi | Penerima melaporkan pemakaian (`USED`/`STORED`/`UNUSED`) |

Detail payload, kode error, dan aturan bisnis lengkap ada di [`backend/README.md`](backend/README.md).

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
| Nama | NIM | Peran / Kontribusi |
| :--- | :--- | :--- |
| _[nama]_ | _[NIM]_ | _[peran]_ |
| _[nama]_ | _[NIM]_ | _[peran]_ |
| _[nama]_ | _[NIM]_ | _[peran]_ |
| _[nama]_ | _[NIM]_ | _[peran]_ |

---
*EcoLoop — Memberi Bahan Sisa Kesempatan Kedua untuk Bumi yang Berkelanjutan.* 🌍🌱
