package com.ecoloop.services

import com.ecoloop.dtos.*
import com.ecoloop.repositories.EcoRepository
import com.ecoloop.repositories.Store
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

class ReservationService(private val repository: EcoRepository) {
    private val random = SecureRandom()
    private val log = LoggerFactory.getLogger(ReservationService::class.java)
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    /** Shared lock order: listing first, reservation second. Never reverse this order. */
    private fun Store.locked(id: UUID): Pair<ListingDto, ReservationDto> {
        val found = reservation(id) ?: missing("Reservasi")
        val listing = listing(uuid(found.listingId), lock = true) ?: missing("Listing")
        return listing to (reservation(id, lock = true) ?: missing("Reservasi"))
    }
    private fun supplier(user: UUID, listing: ListingDto) { if (listing.supplierId != user.toString()) forbidden() }
    private fun state(reservation: ReservationDto, vararg allowed: String) {
        if (reservation.status !in allowed) conflict("INVALID_STATE", "Status reservasi tidak mengizinkan tindakan ini")
    }
    suspend fun create(user: UUID, request: ReservationCreate): ReservationDto {
        val id = uuid(request.listingId)
        val kg = weight(request.requestedKg)
        return repository.transaction {
            val listing = listing(id, lock = true) ?: missing("Listing")
            if (listing.supplierId == user.toString()) conflict("SELF_RESERVATION", "Anda tidak dapat mereservasi listing sendiri")
            if (listing.status != "ACTIVE") conflict("LISTING_INACTIVE", "Listing tidak aktif")
            // Stock is checked at approval, not deducted on request.
            insertReservation(user, id, kg)
        }
    }
    suspend fun list(user: UUID, view: String?): List<ReservationDto> {
        if (view != null && view !in setOf("incoming", "outgoing")) invalid("View harus incoming atau outgoing")
        return repository.transaction { reservations(user, view == "incoming") }
    }
    suspend fun approve(user: UUID, id: UUID): ReservationDto {
        val result = repository.transaction {
            val (listing, reservation) = locked(id)
            supplier(user, listing); state(reservation, "REQUESTED")
            if (listing.status != "ACTIVE") conflict("LISTING_INACTIVE", "Listing tidak aktif")
            if (listing.availableKg < reservation.requestedKg) conflict("INSUFFICIENT_STOCK", "Stok tidak cukup")
            stock(uuid(listing.id), listing.availableKg - reservation.requestedKg)
            reservationState(id, "APPROVED", reservation.requestedKg)
        }
        log.info("reservation={} actor={} status=APPROVED", id, user)
        return result
    }
    suspend fun reject(user: UUID, id: UUID): ReservationDto {
        val result = repository.transaction {
            val (listing, reservation) = locked(id)
            supplier(user, listing); state(reservation, "REQUESTED", "APPROVED")
            if (reservation.status == "APPROVED") stock(uuid(listing.id), listing.availableKg + reservation.reservedKg)
            reservationState(id, "REJECTED", BigDecimal.ZERO)
        }
        log.info("reservation={} actor={} status=REJECTED", id, user)
        return result
    }
    suspend fun cancel(user: UUID, id: UUID): ReservationDto {
        val result = repository.transaction {
            val (listing, reservation) = locked(id)
            if (listing.supplierId != user.toString() && reservation.receiverId != user.toString()) forbidden()
            state(reservation, "REQUESTED", "APPROVED")
            if (reservation.status == "APPROVED") stock(uuid(listing.id), listing.availableKg + reservation.reservedKg)
            reservationState(id, "CANCELLED", BigDecimal.ZERO)
        }
        log.info("reservation={} actor={} status=CANCELLED", id, user)
        return result
    }
    suspend fun handover(user: UUID, id: UUID): TokenDto = repository.transaction {
        val (_, reservation) = locked(id)
        if (reservation.receiverId != user.toString()) forbidden()
        state(reservation, "APPROVED")
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val expires = System.currentTimeMillis() + 15 * 60 * 1000
        saveToken(id, hash(token), expires)
        TokenDto(token, Instant.ofEpochMilli(expires).toString())
    }
    suspend fun complete(user: UUID, id: UUID, request: CompleteRequest): TransferDto {
        val kg = weight(request.actualKg)
        if (request.handoverToken.length !in 32..128) invalid("Token serah terima tidak valid")
        val result = repository.transaction {
            val (listing, reservation) = locked(id)
            supplier(user, listing); state(reservation, "APPROVED")
            val token = token(id) ?: conflict("INVALID_HANDOVER_TOKEN", "Token serah terima tidak valid")
            if (token.usedAt != null) conflict("TOKEN_USED", "Token sudah digunakan")
            if (token.expiresAt <= System.currentTimeMillis()) conflict("TOKEN_EXPIRED", "Token sudah kedaluwarsa")
            if (!MessageDigest.isEqual(token.hash.toByteArray(), hash(request.handoverToken).toByteArray())) conflict("INVALID_HANDOVER_TOKEN", "Token serah terima tidak valid")
            val available = listing.availableKg + reservation.reservedKg - kg
            if (available.signum() < 0) conflict("INSUFFICIENT_STOCK", "Stok tidak cukup untuk berat aktual")
            stock(uuid(listing.id), available)
            consumeToken(id)
            val transfer = insertTransfer(id, kg)
            reservationState(id, "COMPLETED")
            transfer
        }
        log.info("reservation={} actor={} status=COMPLETED transfer={}", id, user, result.id)
        return result
    }
    suspend fun impact(user: UUID) = repository.transaction { impact(user) }
    suspend fun feedback(user: UUID, transfer: UUID, request: FeedbackRequest): FeedbackDto {
        if (request.status !in setOf("USED", "STORED", "UNUSED")) invalid("Status pemakaian harus USED, STORED, atau UNUSED")
        return repository.transaction {
            val reservationId = transferReservation(transfer) ?: missing("Transfer")
            val (_, reservation) = locked(reservationId)
            if (reservation.receiverId != user.toString()) forbidden()
            state(reservation, "COMPLETED")
            saveFeedback(transfer, request.status)
        }
    }
}
