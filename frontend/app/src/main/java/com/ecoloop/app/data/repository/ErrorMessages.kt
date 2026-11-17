package com.ecoloop.app.data.repository

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Mengubah exception jaringan/HTTP menjadi pesan berbahasa Indonesia untuk pengguna. */
fun Throwable.toUserMessage(): String {
    if (this is CancellationException) throw this
    return when (this) {
        is HttpException -> serverMessage() ?: when (code()) {
            400 -> "Data yang dikirim tidak valid."
            401 -> "Sesi berakhir. Silakan masuk kembali."
            403 -> "Kamu tidak memiliki izin untuk tindakan ini."
            404 -> "Data tidak ditemukan."
            409 -> "Stok atau status reservasi sudah berubah. Muat ulang lalu coba lagi."
            429 -> "Terlalu banyak permintaan. Tunggu sebentar."
            else -> "Server sedang bermasalah. Coba lagi."
        }
        is IOException -> "Tidak dapat terhubung ke server. Periksa koneksi atau BASE_URL."
        else -> message ?: "Terjadi kendala. Coba lagi."
    }
}

private fun HttpException.serverMessage(): String? = runCatching {
    val body = response()?.errorBody()?.string() ?: return null
    JsonParser.parseString(body).asJsonObject
        .getAsJsonObject("error")
        ?.get("message")
        ?.asString
}.getOrNull()
