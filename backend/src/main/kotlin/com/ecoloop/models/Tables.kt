package com.ecoloop.models

import org.jetbrains.exposed.dao.id.UUIDTable

object Users : UUIDTable("users") {
    val name = varchar("name", 120)
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val city = varchar("city", 80)
    val district = varchar("district", 80)
    val createdAt = long("created_at")
}
object Categories : UUIDTable("categories") {
    val name = varchar("name", 100)
    val unit = varchar("unit", 10)
}
object Listings : UUIDTable("listings") {
    val supplierId = reference("supplier_id", Users)
    val categoryId = reference("category_id", Categories)
    val title = varchar("title", 160)
    val description = varchar("description", 5000)
    val totalKg = decimal("total_kg", 10, 2)
    val availableKg = decimal("available_kg", 10, 2)
    val district = varchar("district", 80)
    val status = varchar("status", 20)
}
object ListingPhotos : UUIDTable("listing_photos") {
    val listingId = reference("listing_id", Listings)
    val imageUrl = varchar("image_url", 2048)
    val sortOrder = integer("sort_order")
}
object Reservations : UUIDTable("reservations") {
    val listingId = reference("listing_id", Listings)
    val receiverId = reference("receiver_id", Users)
    val requestedKg = decimal("requested_kg", 10, 2)
    val reservedKg = decimal("reserved_kg", 10, 2)
    val status = varchar("status", 20)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
}
object HandoverTokens : UUIDTable("handover_tokens") {
    val reservationId = reference("reservation_id", Reservations).uniqueIndex()
    val tokenHash = varchar("token_hash", 64)
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()
}
object Transfers : UUIDTable("transfers") {
    val reservationId = reference("reservation_id", Reservations).uniqueIndex()
    val actualKg = decimal("actual_kg", 10, 2)
    val completedAt = long("completed_at")
}
object ReuseFeedback : UUIDTable("reuse_feedback") {
    val transferId = reference("transfer_id", Transfers).uniqueIndex()
    val status = varchar("status", 20)
    val reportedAt = long("reported_at")
}
