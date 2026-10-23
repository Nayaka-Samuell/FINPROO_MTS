# EcoLoop Backend

Ktor REST API modular monolith untuk pilot **DKI Jakarta**. Implementasi mengikuti PRD v1 dengan penggantian wilayah Malang sesuai instruksi proyek. Nilai `district`: `Jakarta Selatan`, `Jakarta Pusat`, `Jakarta Barat`, `Jakarta Timur`, `Jakarta Utara` (lima wilayah administratif, bukan nama kecamatan).

## Jalankan

JDK **17+**, Gradle wrapper 9.1.0 (JDK 17–25). Tidak perlu memasang database untuk pengembangan lokal.

```bash
./gradlew run
# Windows CMD/PowerShell: .\gradlew.bat run
```

Server: `http://localhost:8080/api/v1`. Emulator Android memakai `http://10.0.2.2:8080/api/v1`; perangkat fisik memakai IP LAN komputer. HTTP hanya untuk development; gunakan HTTPS melalui reverse proxy pada deployment.

```bash
./gradlew check          # kompilasi + pengujian HTTP, bisnis, konkurensi
./gradlew build          # build + paket distribusi
./gradlew installDist    # build/install/ecoloop-backend/bin/
```

## Environment

| Variabel | Default / fungsi |
| --- | --- |
| `PORT` | `8080` |
| `JDBC_DATABASE_URL` / `DB_URL` | Prioritas pertama JDBC_DATABASE_URL. Jika keduanya tidak ada: `jdbc:h2:./data/ecoloop;MODE=PostgreSQL;DATABASE_TO_UPPER=false` |
| `DB_USER`, `DB_PASSWORD` | Kredensial DB; H2 lokal: `sa` / kosong |
| `JWT_SECRET` | Minimal 32 karakter. Wajib di production. Development membuat secret acak per proses; token lama tidak valid sesudah restart |
| `JWT_ISSUER`, `JWT_AUDIENCE` | `ecoloop`, `ecoloop-android` |
| `APP_ENV` | Set `production` untuk mewajibkan JWT_SECRET dan allowlist CORS eksplisit |
| `CORS_ORIGINS` / `FRONTEND_URL` | Daftar origin dipisahkan koma. Lokal: `http://localhost:3000,http://localhost:5173` |

`.env.example` hanya contoh; aplikasi **tidak** otomatis memuat `.env`. Gunakan environment shell atau secret manager deployment.

Contoh PostgreSQL:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/ecoloop'
export DB_USER='ecoloop'
export DB_PASSWORD='<password-lokal>'
export JWT_SECRET='<secret-acak-minimal-32-karakter>'
./gradlew run
```

URL `postgres://user:password@host:5432/ecoloop` dan `postgresql://...` juga didukung. Parameter JDBC (misalnya `sslmode=require`) dipertahankan. Environment kredensial eksplisit mengungguli userinfo URL. Kegagalan koneksi PostgreSQL **tidak** dialihkan diam-diam ke H2; fallback H2 hanya saat URL tidak dikonfigurasi.

Flyway menjalankan `src/main/resources/db/migration/V1__initial_schema.sql` secara versioned sebelum server menerima trafik. Delapan tabel, foreign keys, unique constraints, CHECK berat/status, indeks, dan seed kategori tersedia. Jangan mengubah migrasi yang sudah diterapkan; tambahkan `V2__...sql` untuk evolusi schema. Timestamp internal disimpan sebagai epoch-millisecond BIGINT dan API mengembalikan ISO-8601 UTC. Berat disimpan sebagai NUMERIC(10,2), diproses menggunakan BigDecimal dan dikirim sebagai angka JSON.

## Kontrak API

Semua payload JSON menggunakan camelCase. Sukses: `{"data": ...}`. Error: `{"error":{"code":"INSUFFICIENT_STOCK","message":"Stok tidak cukup"}}`.

Create mengembalikan 201; aksi/read/update 200. Validasi 400, auth 401, izin 403, resource 404, konflik stok/status/duplikasi 409, rate limit 429. Endpoint bertanda 🔒 memerlukan `Authorization: Bearer <token>`.

| Method | Path `/api/v1` | Payload / catatan |
| --- | --- | --- |
| POST | `/auth/register` | `name,email,password,city,district`; return `{token,user,expiresIn}` |
| POST | `/auth/login` | `email,password`; return `{token,user,expiresIn}` |
| GET / PATCH 🔒 | `/me` | PATCH: `name?,city?,district?` |
| GET | `/categories` | Empat kategori awal, unit kg |
| GET | `/listings` | `categoryId?,district?,q?,page=1,limit=20`; data `{items,page,limit,total}`; hanya ACTIVE |
| GET | `/listings/{id}` | Detail termasuk photos |
| POST 🔒 | `/listings` | `categoryId,title,description,totalKg,district,photos?` |
| PATCH 🔒 | `/listings/{id}` | Field create opsional + `status?`; hanya supplier |
| DELETE 🔒 | `/listings/{id}` | Soft delete → INACTIVE; mengembalikan listing |
| POST 🔒 | `/reservations` | `listingId,requestedKg`; stok belum dikurangi |
| GET 🔒 | `/reservations` | `view=incoming` (supplier) / `outgoing` (receiver, default); termasuk transferId jika selesai |
| POST 🔒 | `/reservations/{id}/approve` | Supplier, REQUESTED → APPROVED, tahan stok |
| POST 🔒 | `/reservations/{id}/reject` | Supplier; REQUESTED/APPROVED → REJECTED; release stok bila perlu |
| POST 🔒 | `/reservations/{id}/cancel` | Supplier atau receiver; REQUESTED/APPROVED → CANCELLED |
| POST 🔒 | `/reservations/{id}/handover-token` | Receiver, APPROVED; return `{token,expiresAt}` |
| POST 🔒 | `/reservations/{id}/complete` | Supplier; `handoverToken,actualKg`; return `{id,reservationId,actualKg,completedAt,status}` |
| GET 🔒 | `/impact/me` | `{totalKg,transferCount,suppliedKg,receivedKg}` dari transfer COMPLETED |
| POST 🔒 | `/transfers/{id}/feedback` | Receiver; `status`: USED (digunakan), STORED (disimpan), UNUSED (tidak terpakai). Upsert laporan terbaru |

### Proyeksi data EcoLoop 2.0

- Semua respons `ListingDto` (katalog, detail, create, patch, delete) menyertakan `categoryName` dari tabel kategori.
- `ReservationDto` menyertakan `listingTitle`, `categoryName`, `listingDistrict`, `supplierName`, dan `receiverName`. Repository mengambil proyeksi melalui join listing, kategori, dan dua alias user; tidak memerlukan fetch detail per reservasi dari Android. Nilainya mengikuti data terbaru, bukan snapshot saat reservasi dibuat.
- Proyeksi yang sama dipakai pada create, incoming/outgoing, approve, reject, cancel, serta pembacaan internal handover/complete. Respons handover tetap `{token,expiresAt}`, dan complete tetap `TransferDto`; refresh GET reservasi setelah complete untuk memperoleh proyeksi berstatus COMPLETED dan `transferId`.
- Filter katalog harus mengirim `categoryId` berupa UUID dari GET `/categories`, misalnya `/api/v1/listings?categoryId=00000000-0000-0000-0000-000000000002`. Filter ini dapat digabung dengan `district`, `q`, `page`, dan `limit`. Nama kategori bukan ID yang valid (400); UUID yang tidak ditemukan menghasilkan daftar kosong.
- Perubahan ini hanya menambah field respons; tidak memerlukan migrasi database. Query proyeksi user hanya memilih nama, bukan password hash atau email.

### Contoh alur

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Penyedia","email":"supplier@example.com","password":"password123","city":"DKI Jakarta","district":"Jakarta Selatan"}'

curl -X POST http://localhost:8080/api/v1/listings \
  -H 'Authorization: Bearer <supplier-token>' -H 'Content-Type: application/json' \
  -d '{"categoryId":"00000000-0000-0000-0000-000000000001","title":"Kain perca bersih","description":"Non-B3 untuk kerajinan","totalKg":10.00,"district":"Jakarta Selatan","photos":[]}'

# Daftarkan akun receiver berbeda, lalu gunakan token receiver:
curl -X POST http://localhost:8080/api/v1/reservations \
  -H 'Authorization: Bearer <receiver-token>' -H 'Content-Type: application/json' \
  -d '{"listingId":"<listing-id>","requestedKg":4.00}'

curl -X POST http://localhost:8080/api/v1/reservations/<id>/approve \
  -H 'Authorization: Bearer <supplier-token>'
curl -X POST http://localhost:8080/api/v1/reservations/<id>/handover-token \
  -H 'Authorization: Bearer <receiver-token>'
curl -X POST http://localhost:8080/api/v1/reservations/<id>/complete \
  -H 'Authorization: Bearer <supplier-token>' -H 'Content-Type: application/json' \
  -d '{"handoverToken":"<raw-token>","actualKg":3.80}'
```

## Aturan bisnis dan keamanan

- Satu akun bisa menjadi supplier sekaligus receiver, tetapi tidak dapat memesan listing sendiri.
- Berat harus > 0 dan maksimal 2 desimal; limit halaman 1–100; delapan foto maksimal. API hanya menerima URL HTTP(S), tidak mengambil URL server-side. File foto diunggah lewat layanan object storage eksternal; endpoint upload multipart `/media` tidak termasuk scope implementasi ini. Validasi MIME/ukuran file harus dilakukan pada layanan upload tersebut.
- Semua mutasi reservasi mengunci **listing lebih dahulu, lalu reservasi** dalam transaksi READ_COMMITTED. Approval, cancel, reject, perubahan stok listing, dan complete terserialisasi per listing. Stok tidak dapat menjadi negatif. Unique reservation_id pada transfer mencegah pencatatan ganda.
- REQUESTED tidak mengurangi stok; stok diperiksa saat approval. Mengubah totalKg menghitung ulang availableKg dengan mempertahankan alokasi/transfer sebelumnya; total tidak boleh lebih kecil dari berat yang telah dialokasikan/disalurkan.
- DELETE hanya menonaktifkan listing, bukan menghapus riwayat. Approval baru ditolak saat listing tidak ACTIVE; reservasi yang sudah APPROVED tetap bisa selesai/dibatalkan.
- Token QR acak 256 bit, SHA-256 di database, berlaku 15 menit, sekali pakai. Menerbitkan token baru mengganti token lama. QR dibuat oleh aplikasi Android dari raw token; tidak menyimpan atau mencatat raw token pada log.
- Berat aktual lebih kecil mengembalikan selisih; lebih besar membutuhkan stok tambahan. Kegagalan complete membatalkan seluruh transaksi termasuk penggunaan token. Replay/repeated actions mengembalikan 409 tanpa perubahan ulang.
- Dampak memakai SQL SUM/COUNT transfer COMPLETED untuk kedua peran akun; suppliedKg dan receivedKg terpisah. Penyaluran tidak otomatis dihitung sebagai penggunaan ulang; feedback melaporkannya terpisah.
- Password bcrypt cost 12, JWT HMAC-SHA256 24 jam, otorisasi resource dan pemeriksaan akun pada setiap request terautentikasi. Tidak ada password/hash pada DTO respons.
- Rate limit proses lokal: 120 request/IP/menit global dan 10 request/IP/menit pada register/login. Identitas IP memakai koneksi langsung, bukan header X-Forwarded-For yang dapat dipalsukan. Di balik reverse proxy, terapkan limit per IP asli pada proxy tepercaya; multi-instance membutuhkan shared limiter di gateway.
- Log hanya mencatat method, path, status serta audit perubahan status reservasi. Jangan menambahkan logging request body, Authorization, password atau token. Konten teks dikembalikan sebagai teks; klien wajib melakukan escaping jika dirender sebagai HTML.
- Tidak ada refresh/revocation endpoint dalam scope ini. Batasi ukuran body/timeouts di reverse proxy untuk deployment publik.

## Struktur

```text
src/main/kotlin/com/ecoloop/
  Application.kt
  plugins/       # HTTP, auth, CORS, errors, pool + migrasi
  routes/        # REST dan DTO binding
  services/      # validasi, otorisasi dan aturan bisnis
  repositories/  # transactional repository + Exposed Store
  models/        # delapan tabel
  dtos/          # kontrak JSON aman dan serializer desimal
src/main/resources/db/migration/
src/test/kotlin/com/ecoloop/  # HTTP E2E, konflik stok, replay, expiry, rollback
```

Pengujian otomatis menggunakan H2 in-memory PostgreSQL mode dengan migrasi yang sama. Dukungan PostgreSQL memakai driver PostgreSQL + modul Flyway PostgreSQL; lakukan deployment smoke test dengan PostgreSQL nyata sebelum production. Pengujian Android fisik/UAT berada di luar backend ini.
