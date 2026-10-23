@file:UseSerializers(DecimalSerializer::class)
package com.ecoloop.dtos

import java.math.BigDecimal
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.DOUBLE)
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }
    override fun deserialize(decoder: Decoder): BigDecimal {
        val value = (decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Berat harus berupa angka")
        return value.content.toBigDecimalOrNull() ?: throw SerializationException("Berat tidak valid")
    }
}
@Serializable data class DataResponse<T>(val data: T)
@Serializable data class ErrorResponse(val error: ErrorBody)
@Serializable data class ErrorBody(val code: String, val message: String)
@Serializable data class RegisterRequest(val name: String, val email: String, val password: String, val city: String, val district: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class ProfilePatch(val name: String? = null, val city: String? = null, val district: String? = null)
@Serializable data class UserDto(val id: String, val name: String, val email: String, val city: String, val district: String, val createdAt: String)
@Serializable data class AuthDto(val token: String, val user: UserDto, val expiresIn: Long = 86400)
@Serializable data class CategoryDto(val id: String, val name: String, val unit: String)
@Serializable data class ListingCreate(val categoryId: String, val title: String, val description: String, val totalKg: BigDecimal, val district: String, val photos: List<String> = emptyList())
@Serializable data class ListingPatch(val categoryId: String? = null, val title: String? = null, val description: String? = null, val totalKg: BigDecimal? = null, val district: String? = null, val status: String? = null, val photos: List<String>? = null)
@Serializable data class ListingDto(
    val id: String,
    val supplierId: String,
    val categoryId: String,
    val title: String,
    val description: String,
    val totalKg: BigDecimal,
    val availableKg: BigDecimal,
    val district: String,
    val status: String,
    val photos: List<String>,
    val categoryName: String = "Kategori"
)
@Serializable data class ListingPage(val items: List<ListingDto>, val page: Int, val limit: Int, val total: Long)
@Serializable data class ReservationCreate(val listingId: String, val requestedKg: BigDecimal)
@Serializable data class ReservationDto(
    val id: String,
    val listingId: String,
    val receiverId: String,
    val supplierId: String,
    val requestedKg: BigDecimal,
    val reservedKg: BigDecimal,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val transferId: String? = null,
    val listingTitle: String = "Material",
    val categoryName: String = "Kategori",
    val listingDistrict: String = "Jakarta",
    val supplierName: String = "Penyedia",
    val receiverName: String = "Penerima"
)
@Serializable data class TokenDto(val token: String, val expiresAt: String)
@Serializable data class CompleteRequest(val handoverToken: String, val actualKg: BigDecimal)
@Serializable data class TransferDto(val id: String, val reservationId: String, val actualKg: BigDecimal, val completedAt: String, val status: String = "COMPLETED")
@Serializable data class ImpactDto(val totalKg: BigDecimal, val transferCount: Long, val suppliedKg: BigDecimal, val receivedKg: BigDecimal)
@Serializable data class FeedbackRequest(val status: String)
@Serializable data class FeedbackDto(val id: String, val transferId: String, val status: String, val reportedAt: String)
