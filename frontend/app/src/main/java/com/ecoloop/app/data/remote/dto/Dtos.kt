package com.ecoloop.app.data.remote.dto

import java.math.BigDecimal

/** Envelope sukses dari backend: `{"data": ...}`. */
data class DataResponse<T>(val data: T)

data class User(
    val id: String,
    val name: String,
    val email: String,
    val city: String,
    val district: String,
)

data class CategoryDto(
    val id: String,
    val name: String,
    val unit: String,
)

data class ListingDto(
    val id: String,
    val supplierId: String,
    val categoryId: String,
    val categoryName: String?,
    val supplierName: String?,
    val title: String,
    val description: String,
    val totalKg: Double,
    val availableKg: Double,
    val district: String,
    val status: String,
    val photos: List<String>?,
) {
    /** URL foto pertama bila penyedia mengunggahnya; null berarti tampilkan ilustrasi kategori. */
    val coverPhoto: String? get() = photos?.firstOrNull { it.isNotBlank() }
}

data class ListingPage(
    val items: List<ListingDto>,
    val page: Int,
    val limit: Int,
    val total: Long,
)

data class ReservationDto(
    val id: String,
    val listingId: String,
    val receiverId: String,
    val supplierId: String,
    val requestedKg: Double,
    val reservedKg: Double,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val transferId: String?,
    val listingTitle: String?,
    val categoryName: String?,
    val listingDistrict: String?,
    val supplierName: String?,
    val receiverName: String?,
    val actualKg: Double?,
    val completedAt: String?,
)

data class TokenDto(val token: String, val expiresAt: String)

data class TransferDto(
    val id: String,
    val reservationId: String,
    val actualKg: Double,
    val completedAt: String,
)

data class ImpactDto(
    val totalKg: Double,
    val transferCount: Long,
    val suppliedKg: Double,
    val receivedKg: Double,
)

data class AuthResponse(val token: String, val user: User)

// ---- Request bodies (berat dikirim sebagai BigDecimal agar presisi 2 desimal terjaga) ----

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val city: String,
    val district: String,
)

data class ListingCreateRequest(
    val categoryId: String,
    val title: String,
    val description: String,
    val totalKg: BigDecimal,
    val district: String,
    val photos: List<String>,
)

data class ReservationRequest(val listingId: String, val requestedKg: BigDecimal)

data class CompleteRequest(val handoverToken: String, val actualKg: BigDecimal)
