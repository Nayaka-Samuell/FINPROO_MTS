# Mengisi data contoh EcoLoop lewat REST API (bukan SQL langsung), sehingga semua
# aturan bisnis backend ikut teruji. Jalankan saat backend aktif:
#   powershell -ExecutionPolicy Bypass -File database\seed.ps1
#   powershell -ExecutionPolicy Bypass -File database\seed.ps1 -BaseUrl http://localhost:8080/api/v1
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1"
)

$ErrorActionPreference = "Stop"
$DemoPassword = "password123"   # akun demo saja, jangan dipakai di production

function Invoke-Eco {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; ContentType = "application/json" }
    if ($null -ne $Body) { $params["Body"] = ($Body | ConvertTo-Json -Depth 5) }
    (Invoke-RestMethod @params).data
}

function Get-Account {
    param([string]$Name, [string]$Email, [string]$District)
    try {
        $auth = Invoke-Eco POST "/auth/register" @{ name = $Name; email = $Email; password = $DemoPassword; city = "DKI Jakarta"; district = $District }
        Write-Host "Registrasi  : $Email"
    } catch {
        $auth = Invoke-Eco POST "/auth/login" @{ email = $Email; password = $DemoPassword }
        Write-Host "Login       : $Email (sudah terdaftar)"
    }
    $auth
}

$supplier = Get-Account "Sari Konveksi" "sari@ecoloop.demo" "Jakarta Selatan"
$receiver = Get-Account "Budi Kriya" "budi@ecoloop.demo" "Jakarta Timur"

$cat = @{
    Kain   = "00000000-0000-0000-0000-000000000001"
    Kardus = "00000000-0000-0000-0000-000000000002"
    Botol  = "00000000-0000-0000-0000-000000000003"
    Kayu   = "00000000-0000-0000-0000-000000000004"
}

$listings = @(
    @{ token = $supplier.token; body = @{ categoryId = $cat.Kain;   title = "Kain perca katun warna-warni"; description = "Sisa potongan katun dari produksi kemeja, bersih dan kering. Cocok untuk patchwork atau keset."; totalKg = 12.50; district = "Jakarta Selatan"; photos = @() } },
    @{ token = $supplier.token; body = @{ categoryId = $cat.Kain;   title = "Perca denim biru";             description = "Potongan denim ukuran 10-40 cm, tanpa kancing logam."; totalKg = 6.00; district = "Jakarta Selatan"; photos = @() } },
    @{ token = $supplier.token; body = @{ categoryId = $cat.Kardus; title = "Kardus packing double wall";   description = "Kardus bekas kiriman bahan, kokoh, sudah dilipat rapi."; totalKg = 20.00; district = "Jakarta Selatan"; photos = @() } },
    @{ token = $receiver.token; body = @{ categoryId = $cat.Botol;  title = "Botol PET 600 ml bersih";      description = "Botol air mineral bekas acara komunitas, sudah dicuci dan tanpa label."; totalKg = 8.75; district = "Jakarta Timur"; photos = @() } },
    @{ token = $receiver.token; body = @{ categoryId = $cat.Kayu;   title = "Potongan kayu pinus palet";    description = "Sisa bongkar palet, paku sudah dicabut. Panjang 30-80 cm."; totalKg = 15.00; district = "Jakarta Timur"; photos = @() } },
    @{ token = $supplier.token; body = @{ categoryId = $cat.Botol;  title = "Botol plastik HDPE warna";     description = "Botol bekas sabun cair yang sudah dibilas, untuk ecobrick atau kerajinan."; totalKg = 4.20; district = "Jakarta Pusat"; photos = @() } }
)

$created = foreach ($item in $listings) {
    $listing = Invoke-Eco POST "/listings" $item.body $item.token
    Write-Host ("Listing     : {0} ({1} kg)" -f $listing.title, $listing.totalKg)
    $listing
}

# Satu transaksi lengkap: REQUESTED -> APPROVED -> handover QR -> COMPLETED
$kain = $created[0]
$reservation = Invoke-Eco POST "/reservations" @{ listingId = $kain.id; requestedKg = 4.00 } $receiver.token
Invoke-Eco POST "/reservations/$($reservation.id)/approve" $null $supplier.token | Out-Null
$handover = Invoke-Eco POST "/reservations/$($reservation.id)/handover-token" $null $receiver.token
$transfer = Invoke-Eco POST "/reservations/$($reservation.id)/complete" @{ handoverToken = $handover.token; actualKg = 3.80 } $supplier.token
Write-Host ("Transaksi   : {0} COMPLETED, berat aktual {1} kg" -f $kain.title, $transfer.actualKg)

# Satu permintaan masih menunggu, untuk demo tombol Setujui/Tolak
$kardus = $created[2]
Invoke-Eco POST "/reservations" @{ listingId = $kardus.id; requestedKg = 5.00 } $receiver.token | Out-Null
Write-Host "Reservasi   : $($kardus.title) 5 kg (REQUESTED)"

Write-Host ""
Write-Host "Selesai. Akun demo (password: $DemoPassword):"
Write-Host "  - sari@ecoloop.demo  (penyedia, Jakarta Selatan)"
Write-Host "  - budi@ecoloop.demo  (penerima, Jakarta Timur)"
