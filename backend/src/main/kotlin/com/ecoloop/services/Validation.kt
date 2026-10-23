package com.ecoloop.services

import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

class ApiException(val status: HttpStatusCode, val code: String, override val message: String) : RuntimeException(message)
fun invalid(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message)
fun missing(resource: String): Nothing = throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "$resource tidak ditemukan")
fun forbidden(): Nothing = throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Anda tidak memiliki izin untuk tindakan ini")
fun conflict(code: String, message: String): Nothing = throw ApiException(HttpStatusCode.Conflict, code, message)
fun uuid(value: String): UUID = try {
    UUID.fromString(value).also { if (!it.toString().equals(value, true)) invalid("Format ID tidak valid") }
} catch (_: IllegalArgumentException) { invalid("Format ID tidak valid") }
fun text(value: String, field: String, max: Int): String = value.trim().also {
    if (it.isEmpty() || it.length > max || it.any { c -> c.isISOControl() && c != '\n' && c != '\t' }) invalid("$field wajib diisi, maksimal $max karakter")
}
fun weight(value: BigDecimal): BigDecimal {
    if (value <= BigDecimal.ZERO || value > BigDecimal("99999999.99") || value.scale() > 2) invalid("Berat harus positif, maksimal 99999999.99 kg dan 2 angka desimal")
    return value.setScale(2)
}
val districts = setOf("Jakarta Selatan", "Jakarta Pusat", "Jakarta Barat", "Jakarta Timur", "Jakarta Utara")
fun district(value: String): String = value.trim().also { if (it !in districts) invalid("Wilayah harus salah satu dari lima wilayah Jakarta") }
fun city(value: String): String = value.trim().also {
    if (!it.equals("DKI Jakarta", true) && !it.equals("Jakarta", true)) invalid("Pilot hanya tersedia di DKI Jakarta")
}
fun email(value: String): String = value.trim().lowercase().also {
    if (it.length > 254 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(it)) invalid("Email tidak valid")
}
fun photos(values: List<String>): List<String> {
    if (values.size > 8) invalid("Maksimal 8 foto per listing")
    return values.map { value ->
        val uri = try { URI(value) } catch (_: Exception) { invalid("URL foto tidak valid") }
        if (value.length > 2048 || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) invalid("Foto harus berupa URL HTTP/HTTPS yang valid")
        value
    }
}
