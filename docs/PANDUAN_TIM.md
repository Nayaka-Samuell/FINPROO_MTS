# Panduan Tim EcoLoop

Dokumen ini untuk anggota tim yang baru pertama kali membuka proyek. Baca berurutan sekali (±20 menit), setelah itu pakai sebagai rujukan.

**Isi:**
1. [EcoLoop itu apa?](#1-ecoloop-itu-apa)
2. [Gambaran sistem](#2-gambaran-sistem)
3. [Menjalankan proyek pertama kali](#3-menjalankan-proyek-pertama-kali)
4. [Coba alur lengkap pakai akun demo](#4-coba-alur-lengkap-pakai-akun-demo)
5. [Peta kode Android: "mau ubah X, buka file Y"](#5-peta-kode-android)
6. [Cara data mengalir di aplikasi](#6-cara-data-mengalir-di-aplikasi)
7. [Konsep penting yang dipakai di mana-mana](#7-konsep-penting)
8. [Backend & aturan bisnis](#8-backend--aturan-bisnis)
9. [Resep: menambah fitur baru](#9-resep-menambah-fitur-baru)
10. [Aturan kerja bareng pakai Git](#10-aturan-kerja-bareng-pakai-git)
11. [Pembagian tugas yang masih tersisa](#11-pembagian-tugas-yang-masih-tersisa)
12. [Kalau ada masalah (troubleshooting)](#12-troubleshooting)

---

## 1. EcoLoop itu apa?

Aplikasi Android yang mempertemukan **pemilik bahan sisa** (penjahit, toko, UMKM) dengan **orang yang bisa memakainya lagi** (pengrajin, komunitas daur ulang). Contoh bahannya: kain perca, kardus, botol plastik, potongan kayu.

- Gratis dan diambil sendiri, tanpa kurir dan tanpa pembayaran.
- Wilayah pilot: 5 kota administrasi di DKI Jakarta.
- Satuan: kilogram, maksimal 2 angka di belakang koma.
- Satu akun bisa jadi **penyedia** (membagikan) sekaligus **penerima** (memesan).
- Mendukung **SDG 12.5**: mengurangi sampah lewat reuse dan recycle.

---

## 2. Gambaran sistem

```
┌────────────────────┐     HTTP + JSON      ┌────────────────────┐      SQL      ┌──────────────┐
│  Aplikasi Android  │  ─────────────────►  │   Backend (Ktor)   │  ──────────►  │  PostgreSQL  │
│  folder frontend/  │  ◄─────────────────  │   folder backend/  │  ◄──────────  │   (Docker)   │
└────────────────────┘   /api/v1/...        └────────────────────┘               └──────────────┘
   Kotlin + Jetpack Compose                    Kotlin + Ktor + Exposed              8 tabel
   Tampilan & interaksi                        Aturan bisnis, keamanan, stok        Data permanen
```

Prinsip utamanya: **Android hanya menampilkan data dan mengirim permintaan.** Semua keputusan penting (stok cukup atau tidak, siapa yang boleh menyetujui, token QR valid atau tidak) diputuskan oleh **backend**. Jadi meskipun ada bug di tampilan, data di database tetap benar.

---

## 3. Menjalankan proyek pertama kali

Yang perlu di-install: **JDK 17+**, **Android Studio**, **Docker Desktop**, **Git**.

```powershell
# 1. Clone
git clone https://github.com/Nayaka-Samuell/FINPROO_MTS.git
cd FINPROO_MTS

# 2. Nyalakan database (sekali saja; berikutnya cukup: docker start ecoloop-postgres)
docker run -d --name ecoloop-postgres -e POSTGRES_DB=ecoloop -e POSTGRES_USER=ecoloop -e POSTGRES_PASSWORD=ecoloop123 -p 5432:5432 postgres:16-alpine

# 3. Isi data contoh
docker cp database/ecoloop_dump.sql ecoloop-postgres:/tmp/ecoloop_dump.sql
docker exec ecoloop-postgres psql -U ecoloop -d ecoloop -v ON_ERROR_STOP=1 -f /tmp/ecoloop_dump.sql

# 4. Jalankan backend (biarkan terminal ini terbuka)
cd backend
$env:DB_URL = "jdbc:postgresql://localhost:5432/ecoloop"
$env:DB_USER = "ecoloop"
$env:DB_PASSWORD = "ecoloop123"
$env:JWT_SECRET = "rahasia-lokal-minimal-tiga-puluh-dua-karakter"
.\gradlew.bat run
```

Tunggu sampai muncul `Responding at http://127.0.0.1:8080`.

5. Buka **Android Studio** → *File > Open* → pilih folder `frontend` → tunggu Gradle Sync → **Run ▶** di emulator.

> `ecoloop123` cuma password lokal di laptop masing-masing, boleh diganti. Jangan pernah commit password asli.
> Mau pakai HP fisik? Lihat README bagian 4D (`-PBASE_URL=http://<IP-laptop>:8080/api/v1/`).

---

## 4. Coba alur lengkap pakai akun demo

| Akun | Peran di data contoh | Password |
| :--- | :--- | :--- |
| `sari@ecoloop.demo` | Penyedia (Jakarta Selatan) | `password123` |
| `budi@ecoloop.demo` | Penerima (Jakarta Timur) | `password123` |

Ikuti urutan ini sekali supaya paham seluruh alur aplikasi:

| Langkah | Login sebagai | Yang dilakukan | Status reservasi |
| :-: | :--- | :--- | :--- |
| 1 | Sari | Beranda → **Bagikan** → isi form → Terbitkan | — |
| 2 | Budi | Beranda → buka material Sari → **Ajukan reservasi** (mis. 2 kg) | `REQUESTED` |
| 3 | Sari | Aktivitas → tab **Masuk** → **Setujui** | `APPROVED` (stok dipotong) |
| 4 | Budi | Aktivitas → tab **Keluar** → **Tampilkan QR** | QR berlaku 15 menit |
| 5 | Sari | Aktivitas → Masuk → **Scan QR** (atau *Masukkan token manual*) → isi berat aktual | `COMPLETED` |
| 6 | Keduanya | Buka tab **Dampak** → angka kg bertambah | — |

Tips: emulator tidak punya kamera sungguhan. Untuk uji di satu laptop, salin token dari QR lewat input manual, atau pakai HP fisik untuk scan.

---

## 5. Peta kode Android

Semua kode ada di `frontend/app/src/main/java/com/ecoloop/app/`.

```
com/ecoloop/app/
├── MainActivity.kt          ← titik awal aplikasi (hampir tidak perlu diubah)
├── EcoLoopApplication.kt    ← membuat AppContainer sekali saat app dibuka
├── AppContainer.kt          ← "gudang" objek bersama: API, sesi, repository
│
├── data/                    ← SEMUA urusan ambil/kirim data
│   ├── remote/EcoApi.kt         daftar endpoint backend (Retrofit)
│   ├── remote/ApiClient.kt      setting HTTP + pasang token + tangani 401
│   ├── remote/dto/Dtos.kt       bentuk data JSON (User, ListingDto, dst.)
│   ├── local/SessionStore.kt    simpan token login di HP
│   └── repository/              jembatan antara layar dan API
│
├── ui/                      ← hal-hal tampilan yang dipakai bersama
│   ├── theme/                   warna, font, bentuk sudut
│   ├── components/              tombol, field, kartu, badge, skeleton, dll.
│   ├── navigation/              daftar halaman + bottom bar
│   ├── UiState.kt               Loading / Success / Error
│   └── Formatters.kt            format "12,5 kg", tanggal, validasi berat
│
└── feature/                 ← SATU FOLDER PER LAYAR
    ├── auth/        Login & Daftar
    ├── home/        Beranda
    ├── detail/      Detail material
    ├── listing/     Form bagikan material
    ├── activity/    Aktivitas + QR + scanner
    ├── impact/      Dampak
    └── profile/     Profil
```

Setiap folder `feature/xxx/` berisi dua file:
- **`XxxScreen.kt`**: tampilan saja (apa yang dilihat dan ditekan pengguna).
- **`XxxViewModel.kt`**: logika (ambil data, validasi, apa yang terjadi saat tombol ditekan).

### "Mau ubah X, buka file Y"

| Mau ubah… | Buka file |
| :--- | :--- |
| Warna aplikasi | `ui/theme/Color.kt` |
| Ukuran/ketebalan huruf | `ui/theme/Type.kt` |
| Ikon & warna tiap kategori (kain pink, dll.) | `ui/components/CategoryArt.kt` |
| Tampilan kartu material di Beranda | `ui/components/EcoMaterialCard.kt` |
| Teks/warna badge status ("Siap diambil", dll.) | `ui/components/EcoStatusBadge.kt` |
| Menu bottom bar (label/ikon) | `ui/navigation/Routes.kt` |
| Menambah halaman baru ke navigasi | `ui/navigation/EcoNavHost.kt` |
| Tampilan Beranda (banner, search, grid) | `feature/home/HomeScreen.kt` |
| Kecepatan debounce pencarian (350 ms) | `feature/home/HomeViewModel.kt` |
| Aturan validasi berat (koma, 2 desimal) | `ui/Formatters.kt` → `validateWeight` |
| Isi form Bagikan material | `feature/listing/AddListingScreen.kt` + `AddListingViewModel.kt` |
| Tombol Setujui/Tolak/Batalkan/Scan | `feature/activity/ActivityScreen.kt` → `ReservationActions` |
| Kamera scanner QR | `feature/activity/scan/QrScanner.kt` |
| Pesan error berbahasa Indonesia | `data/repository/ErrorMessages.kt` |
| Alamat server default (`BASE_URL`) | `frontend/app/build.gradle.kts` |
| Daftar wilayah Jakarta | `data/repository/Repositories.kt` → `DISTRICTS` |

---

## 6. Cara data mengalir di aplikasi

Contoh: pengguna membuka **Beranda**.

```
HomeScreen.kt          (1) tampil, minta data ke ViewModel
     │
     ▼
HomeViewModel.kt       (2) set state = Loading, lalu panggil repository
     │
     ▼
CatalogRepository      (3) panggil api.listings(...)
     │
     ▼
EcoApi + ApiClient     (4) kirim GET /api/v1/listings + header token
     │
     ▼
Backend Ktor           (5) query PostgreSQL, balas JSON {"data": {...}}
     │
     ▼
HomeViewModel.kt       (6) state = Success(daftar) atau Error(pesan)
     │
     ▼
HomeScreen.kt          (7) Compose otomatis menggambar ulang: grid / pesan error
```

Aturan emasnya: **Screen tidak pernah memanggil API langsung.** Screen hanya membaca `state` dari ViewModel dan memanggil fungsi ViewModel (mis. `viewModel.refresh()`).

---

## 7. Konsep penting

**`UiState` (Loading / Success / Error).** Setiap data dari server punya salah satu dari 3 keadaan, dan layar menangani ketiganya:

```kotlin
when (val listings = state.listings) {
    UiState.Loading -> skeleton
    is UiState.Error -> ErrorPanel(listings.message, onRetry = ...)
    is UiState.Success -> tampilkan listings.data
}
```

**`DataChanges`.** Setelah aksi yang mengubah data (reservasi, setujui, selesai, tambah listing), repository mengirim sinyal "data berubah". Beranda, Aktivitas, dan Dampak mendengarkan sinyal ini lalu memuat ulang otomatis. Jadi kamu **tidak perlu** memanggil refresh manual di layar lain.

**Snackbar (`Messenger`).** Pesan singkat di bawah layar. Di ViewModel, kirim lewat `_messages.send("teks")`. Di Screen, cukup panggil `CollectMessages(viewModel.messages)`.

**Sesi & 401.** Token login disimpan oleh `SessionStore`. Kalau server membalas 401 (token kedaluwarsa), `ApiClient` otomatis menghapus token dan aplikasi kembali ke layar login. Tidak perlu ditangani per layar.

**Validasi inline.** Error tampil di bawah field (bukan pop-up). Pola yang dipakai: error baru ditampilkan setelah pengguna mengetik atau menekan tombol kirim (`showErrors`).

---

## 8. Backend & aturan bisnis

```
backend/src/main/kotlin/com/ecoloop/
├── Application.kt     ← titik awal server
├── routes/            ← daftar URL endpoint (ApiRoutes.kt)
├── services/          ← ATURAN BISNIS ada di sini
│   ├── ReservationService.kt   setujui/tolak/batal/QR/selesai
│   ├── CatalogService.kt       listing
│   ├── AuthService.kt          daftar/login/JWT
│   └── Validation.kt           validasi berat, wilayah, email, dll.
├── repositories/      ← query database (Exposed)
├── models/            ← definisi tabel
└── dtos/              ← bentuk JSON request/response
```

### Status reservasi: siapa boleh apa

| Dari status | Aksi | Siapa | Ke status | Efek ke stok |
| :--- | :--- | :--- | :--- | :--- |
| — | Ajukan | Penerima | `REQUESTED` | Belum dipotong |
| `REQUESTED` | Setujui | Penyedia | `APPROVED` | **Dipotong** |
| `REQUESTED` / `APPROVED` | Tolak | Penyedia | `REJECTED` | Dikembalikan (jika sudah dipotong) |
| `REQUESTED` / `APPROVED` | Batalkan | Penyedia / Penerima | `CANCELLED` | Dikembalikan (jika sudah dipotong) |
| `APPROVED` | Scan QR + berat aktual | Penyedia | `COMPLETED` | Disesuaikan dengan berat aktual |

Pengujian otomatis backend: `cd backend` lalu `.\gradlew.bat test`. **Wajib lulus sebelum push** kalau kamu mengubah backend.

---

## 9. Resep: menambah fitur baru

Contoh: menambah layar **"Edit Profil"**.

1. **Endpoint sudah ada?** Cek `backend/.../routes/ApiRoutes.kt` (ternyata `PATCH /me` sudah ada).
2. **Tambah ke API Android:** di `data/remote/EcoApi.kt` tambahkan
   `@PATCH("me") suspend fun updateMe(@Body body: ProfilePatch): DataResponse<User>`,
   lalu buat `data class ProfilePatch(...)` di `dto/Dtos.kt`.
3. **Tambah fungsi di repository:** `AuthRepository.updateProfile(...)`.
4. **Buat folder fitur:** `feature/profile/EditProfileViewModel.kt` dan `EditProfileScreen.kt`. Contek pola dari `feature/listing/` (form + validasi + snackbar).
5. **Daftarkan halaman:** tambah route di `ui/navigation/Routes.kt`, lalu `composable(...)` di `EcoNavHost.kt`.
6. **Tes:** `cd frontend` lalu `.\gradlew.bat assembleDebug`, kemudian coba di emulator.

---

## 10. Aturan kerja bareng pakai Git

**Jangan kerja langsung di `main`.** Setiap orang kerja di branch sendiri.

```powershell
# Mulai kerja
git checkout main
git pull                                  # ambil update terbaru dulu!
git checkout -b nama/fitur-singkat        # contoh: dina/edit-profil

# ... ngoding ...

git add .
git commit -m "feat: tambah layar edit profil"
git push -u origin nama/fitur-singkat
```

Lalu buka GitHub → **Compare & pull request** → minta satu teman review → **Merge**.

**Format pesan commit:**
- `feat: …` untuk fitur baru
- `fix: …` untuk perbaikan bug
- `docs: …` untuk README atau laporan
- `refactor: …` untuk merapikan kode tanpa mengubah perilaku

**Dilarang di-commit** (sudah diblokir `.gitignore`, tapi tetap hati-hati):
- password asli, file `.env`, `local.properties`
- file `.apk`, folder `build/`, `backend/data/`
- file pribadi PDF/TXT

**Bentrok (conflict) saat merge?** Jangan panik. Buka file yang ditandai `<<<<<<<`, pilih bagian yang benar, hapus tanda-tandanya, lalu `git add` dan `git commit`. Kalau ragu, tanya di grup sebelum force push.

> Supaya tidak bentrok: **bagi kerja per folder `feature/`**. Dua orang jangan mengedit file yang sama di waktu yang sama.

---

## 11. Pembagian tugas yang masih tersisa

Silakan isi nama penanggung jawab:

| Tugas | Lokasi | PJ |
| :--- | :--- | :--- |
| Uji semua alur di emulator + HP fisik (bagian 4) | — | _[nama]_ |
| Ambil 10 screenshot | `docs/Final_Report_EcoLoop.md` bagian 4 | _[nama]_ |
| Isi data pendukung latar belakang (+ sumber) | Laporan bagian 1.1 | _[nama]_ |
| Lengkapi Challenges & Learnings | Laporan bagian 5 | _[nama]_ |
| Isi AI Disclosure | Laporan bagian 6 | _[nama]_ |
| Isi tabel anggota tim | `README.md` bagian 7 | _[nama]_ |
| Siapkan slide + demo presentasi | — | _[nama]_ |
| _[fitur tambahan, mis. Edit Profil / feedback pemakaian]_ | — | _[nama]_ |

---

## 12. Troubleshooting

| Masalah | Penyebab & solusi |
| :--- | :--- |
| App: "Tidak dapat terhubung ke server" | Backend belum jalan, atau `BASE_URL` salah. Emulator pakai `10.0.2.2`, HP fisik pakai IP laptop. Cek juga firewall port 8080. |
| Tiba-tiba keluar ke layar login | Normal kalau backend di-restart tanpa `JWT_SECRET` (token lama jadi tidak valid). Set `JWT_SECRET` tetap seperti di bagian 3. |
| Backend: `password authentication failed` | `DB_PASSWORD` tidak sama dengan saat `docker run`. |
| Backend: port 5432 / 8080 sudah dipakai | Ada Postgres/backend lain yang menyala. Matikan, atau ganti port. |
| `docker: cannot connect to the Docker daemon` | Buka Docker Desktop dulu, tunggu sampai statusnya *running*. |
| Gradle Sync gagal di Android Studio | Pastikan JDK 17+ (*Settings > Build Tools > Gradle > Gradle JDK*), lalu *File > Invalidate Caches*. |
| Scanner tidak membuka kamera | Izin kamera ditolak: buka Pengaturan HP → Aplikasi → EcoLoop → Izin → Kamera. Atau pakai *Masukkan token manual*. |
| QR "kedaluwarsa" | Token hanya berlaku 15 menit. Tutup dialog lalu tekan *Tampilkan QR* lagi. |
| Ingin reset data ke kondisi awal | Ulangi langkah import dump (bagian 3 nomor 3). |
