# EcoLoop — Final Report

**Mata kuliah:** Mobile Technology Solution (MTS LEC)
**Tim:** Tim 5 — _[isi nama & NIM anggota]_
**Versi aplikasi:** EcoLoop 2.0 (Android + Ktor REST API)
**Status dokumen:** DRAFT — periksa, lengkapi, dan sesuaikan sebelum dikumpulkan.

---

## 1. Introduction

### 1.1 Latar Belakang

Usaha kecil di DKI Jakarta, seperti konveksi, toko ritel, dan UMKM pengemasan, setiap hari menghasilkan **material sisa non-B3**: kain perca, kardus, botol plastik, dan potongan kayu. Material ini sebenarnya masih bisa dipakai oleh pengrajin, seniman daur ulang, komunitas sirkular, dan pembuat ecobrick. Namun kedua pihak tidak punya saluran yang mudah untuk saling menemukan. Akibatnya material yang masih bernilai berakhir di tempat sampah atau TPA.

Saluran informal yang ada, seperti grup chat dan media sosial, punya beberapa masalah:

- stok tidak tercatat, sehingga satu material bisa "dijanjikan" ke beberapa orang sekaligus (*double booking*);
- tidak ada bukti bahwa serah terima benar-benar terjadi;
- dampak lingkungan (berapa kg yang benar-benar tersalurkan) tidak terukur.

_[Tambahkan data pendukung, mis. timbulan sampah DKI Jakarta dari SIPSN KLHK, beserta sumbernya.]_

### 1.2 Tujuan

1. Menyediakan katalog material sisa non-B3 yang bisa dicari berdasarkan kategori, kata kunci, dan wilayah di DKI Jakarta.
2. Menyediakan alur reservasi yang mencegah *double booking* lewat stok yang dikunci saat persetujuan.
3. Memverifikasi serah terima fisik dengan QR sekali pakai dan mencatat **berat aktual**.
4. Menampilkan dampak terverifikasi (kg tersalurkan) untuk setiap akun sebagai kontribusi terhadap SDG 12.5.

### 1.3 Solusi

**EcoLoop** adalah aplikasi Android (Kotlin, Jetpack Compose, Material 3) yang terhubung ke REST API Ktor dengan database PostgreSQL. Satu akun bisa berperan sebagai **penyedia** (membagikan material) sekaligus **penerima** (memesan material). Penyaluran bersifat gratis dengan pengambilan mandiri, tanpa kurir dan tanpa pembayaran.

Alur utama:

```
Penyedia membagikan listing → Penerima mencari & mengajukan reservasi (REQUESTED)
→ Penyedia menyetujui, stok ditahan (APPROVED)
→ Penerima menampilkan QR sekali pakai → Penyedia memindai QR & memasukkan berat aktual
→ Transaksi COMPLETED → angka dampak bertambah
```

---

## 2. SDG Justification

**SDG 12 — Responsible Consumption and Production**
**Target 12.5:** *"By 2030, substantially reduce waste generation through prevention, reduction, recycling and reuse."*

EcoLoop mendukung target ini secara langsung:

| Aspek Target 12.5 | Wujud di EcoLoop |
| :--- | :--- |
| **Reuse** | Material sisa disalurkan utuh ke penerima yang memakainya kembali sebagai bahan baku kerajinan atau produk. |
| **Recycling** | Kategori botol plastik dan kardus menyasar pengepul atau komunitas daur ulang dan pembuat ecobrick. |
| **Prevention / reduction** | Material dicegat sebelum masuk aliran sampah, sehingga timbulan sampah di TPA berkurang. |
| **Pengukuran** | Dampak hanya dihitung dari transaksi `COMPLETED` dengan berat aktual yang ditimbang saat serah terima, bukan estimasi. Indikator ini sejalan dengan semangat indikator 12.5.1 (*national recycling rate, tons of material recycled*). |

Fitur feedback pemakaian (`USED`, `STORED`, `UNUSED`) di API memisahkan "tersalurkan" dari "benar-benar dipakai", sehingga klaim dampak tidak dilebih-lebihkan.

_[Opsional: kaitkan juga dengan SDG 11.6 (pengelolaan sampah kota) bila relevan.]_

---

## 3. Features Overview

| Fitur | Deskripsi |
| :--- | :--- |
| **Autentikasi** | Daftar dan masuk dengan email/password. Validasi inline (format email, password minimal 8 karakter) dan tombol lihat password. Token JWT disimpan di DataStore. Bila server membalas 401, sesi dihapus dan pengguna kembali ke layar login. |
| **Beranda** | Sapaan dan wilayah pengguna, banner total kg tersalurkan (`/impact/me`), pencarian dengan *debounce* 350 ms, chip kategori dengan ikon, filter wilayah lewat satu menu, grid 2 kolom, *pull-to-refresh*, dan *skeleton loading*. |
| **Ilustrasi kategori** | Setiap kategori punya ikon dan warna latar sendiri (kain merah muda, kardus kuning amber, botol biru, kayu hijau). Foto dari penyedia ditampilkan bila ada. |
| **Detail material** | Header ilustrasi/foto, progress bar stok (tersedia/total), kartu penyedia, deskripsi, dan tombol "Ajukan reservasi" yang menempel di bawah. Berat divalidasi inline: harus > 0, maksimal 2 desimal, koma diterima, tidak melebihi stok. |
| **Bagikan material** | Form judul, kategori, berat, wilayah, deskripsi, dan URL foto opsional dengan validasi inline. Setelah berhasil muncul snackbar lalu aplikasi kembali ke Beranda. |
| **Aktivitas** | Tab Masuk (sebagai penyedia) dan Keluar (sebagai penerima), badge status, dan timeline Diminta → Disetujui → Selesai. Penyedia bisa Setujui atau Tolak. Penerima bisa Batalkan atau Tampilkan QR. |
| **Serah terima QR** | Penerima menampilkan QR sekali pakai yang berlaku 15 menit dengan hitung mundur. Penyedia menekan "Scan QR": izin kamera diminta, CameraX + ML Kit membaca token, lalu form berat aktual terbuka otomatis. Input token manual tersedia sebagai cadangan. |
| **Dampak** | Angka kg beranimasi, rincian kg disalurkan dan diterima, jumlah transfer, dan riwayat transfer selesai dengan berat aktual. |
| **Profil** | Data akun dan tombol keluar dengan konfirmasi. |
| **Backend** | Ktor + Exposed + Flyway + PostgreSQL. Stok dikunci per listing (`SELECT … FOR UPDATE`), token QR disimpan sebagai hash SHA-256, password memakai bcrypt, ada rate limiting, dan pengujian otomatis (HTTP, aturan bisnis, konkurensi). |

**Arsitektur Android:** single-activity, `feature/*` (Screen + ViewModel per fitur), `data/*` (remote, local, repository), `ui/*` (theme, components, navigation), dan `AppContainer` sebagai *dependency container* sederhana. Setiap layar data memakai `sealed UiState` (Loading/Success/Error).

---

## 4. App Screenshot

> _[Isi dengan screenshot dari emulator atau HP. Login dengan akun demo `sari@ecoloop.demo` / `budi@ecoloop.demo` (password `password123`) setelah meng-import `database/ecoloop_dump.sql`.]_

| No | Layar | Screenshot |
| :-: | :--- | :--- |
| 1 | Login / Daftar | _[screenshot]_ |
| 2 | Beranda (banner, pencarian, chip kategori, grid) | _[screenshot]_ |
| 3 | Beranda — filter wilayah / hasil kosong | _[screenshot]_ |
| 4 | Detail material + dialog reservasi | _[screenshot]_ |
| 5 | Bagikan material (validasi inline) | _[screenshot]_ |
| 6 | Aktivitas — tab Masuk (Setujui/Tolak, Scan QR) | _[screenshot]_ |
| 7 | Aktivitas — tab Keluar (QR serah terima, Batalkan) | _[screenshot]_ |
| 8 | Scanner QR + form berat aktual | _[screenshot]_ |
| 9 | Dampak (angka kg & riwayat) | _[screenshot]_ |
| 10 | Profil | _[screenshot]_ |

---

## 5. Challenges and Learnings

> _Draft. Sesuaikan dengan pengalaman nyata tim; hapus poin yang tidak kalian alami._

**Challenges**

1. **Konsistensi stok saat banyak permintaan bersamaan.** Dua penerima bisa memesan stok yang sama. Solusinya, stok tidak dipotong saat `REQUESTED`, melainkan dikunci dan dipotong saat `APPROVED` di dalam transaksi database dengan urutan kunci yang tetap (listing lalu reservasi) agar tidak terjadi *deadlock*.
2. **Verifikasi serah terima yang aman.** Token QR harus tidak bisa ditebak, sekali pakai, dan berumur pendek. Server hanya menyimpan hash-nya, dan QR digambar di perangkat penerima.
3. **Struktur kode Android.** Versi awal menumpuk semua logika di dua file dengan baris sangat panjang, sehingga sulit dibaca dan di-*review*. Kode lalu dipecah per fitur dengan ViewModel dan `UiState` masing-masing.
4. **Navigasi Compose.** Bottom bar membaca `navController.currentDestination` secara langsung sehingga tidak ikut ter-*recompose*. Masalah ini diperbaiki dengan `currentBackStackEntryAsState()`. Detail listing kini memakai route `detail/{id}` dan memuat ulang dari API, bukan dari objek yang disimpan di variabel.
5. **State loading yang macet.** Bila pencarian gagal, indikator loading tidak pernah berhenti. Kini setiap request diakhiri dengan `Success` atau `Error`, dan request lama dibatalkan (`collectLatest`) saat filter berubah.
6. **Jaringan emulator vs HP fisik.** Emulator memakai `10.0.2.2`, sedangkan HP fisik memakai IP LAN. Masalah ini diselesaikan dengan `BASE_URL` yang bisa di-override lewat `-PBASE_URL`.

**Learnings**

- Aturan bisnis yang kritis (stok, status, token) harus dijaga di server, bukan hanya di UI.
- *Unidirectional data flow* (ViewModel → `StateFlow` → Compose) membuat perilaku layar lebih mudah ditebak dan diuji.
- Validasi inline dan pesan error berbahasa manusia sangat memengaruhi pengalaman pengguna.
- _[Tambahkan pembelajaran tim: pembagian kerja, Git, presentasi, dsb.]_

---

## 6. AI Disclosure

> _[Diisi oleh tim. Jelaskan secara jujur:]_
> - _[Tools AI apa saja yang dipakai (nama tool dan model).]_
> - _[Bagian mana yang dibantu AI: kode, desain, dokumentasi, laporan.]_
> - _[Bagian mana yang dikerjakan tim sendiri.]_
> - _[Bagaimana tim memeriksa dan menguji hasil dari AI.]_

_[isi di sini]_