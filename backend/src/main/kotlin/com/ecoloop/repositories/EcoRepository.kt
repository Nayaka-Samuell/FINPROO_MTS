package com.ecoloop.repositories

import com.ecoloop.dtos.*
import com.ecoloop.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface EcoRepository {
    suspend fun <T> transaction(block: Store.() -> T): T
}
class ExposedEcoRepository(private val database: Database) : EcoRepository {
    override suspend fun <T> transaction(block: Store.() -> T): T = withContext(Dispatchers.IO) {
        transaction(database) { maxAttempts = 1; Store().block() }
    }
}
data class UserRecord(val profile: UserDto, val passwordHash: String)
data class TokenRecord(val hash: String, val expiresAt: Long, val usedAt: Long?)
private fun Long.iso() = Instant.ofEpochMilli(this).toString()

/** All methods run inside the repository transaction; locks are held until commit. */
class Store {
    fun user(id: UUID): UserRecord? = Users.selectAll().where { Users.id eq id }.singleOrNull()?.let(::userRecord)
    fun userByEmail(email: String): UserRecord? = Users.selectAll().where { Users.email eq email }.singleOrNull()?.let(::userRecord)
    private fun userRecord(row: ResultRow) = UserRecord(UserDto(row[Users.id].value.toString(), row[Users.name], row[Users.email], row[Users.city], row[Users.district], row[Users.createdAt].iso()), row[Users.passwordHash])
    fun insertUser(name: String, email: String, passwordHash: String, city: String, district: String): UserDto {
        val id = Users.insertAndGetId {
            it[Users.name] = name; it[Users.email] = email; it[Users.passwordHash] = passwordHash
            it[Users.city] = city; it[Users.district] = district; it[createdAt] = System.currentTimeMillis()
        }.value
        return user(id)!!.profile
    }
    fun updateProfile(id: UUID, patch: ProfilePatch): UserDto {
        Users.update({ Users.id eq id }) {
            patch.name?.let { v -> it[name] = v }; patch.city?.let { v -> it[city] = v }; patch.district?.let { v -> it[district] = v }
        }
        return user(id)!!.profile
    }
    fun categories() = Categories.selectAll().orderBy(Categories.name).map { CategoryDto(it[Categories.id].value.toString(), it[Categories.name], it[Categories.unit]) }
    fun categoryExists(id: UUID) = Categories.selectAll().where { Categories.id eq id }.count() > 0
    private val listingSupplier = Users.alias("listing_supplier")
    private fun listingProjection() = (Listings innerJoin Categories)
        .join(listingSupplier, JoinType.INNER, Listings.supplierId, listingSupplier[Users.id])
        .select(Listings.columns + Categories.name + listingSupplier[Users.name])
    fun listing(id: UUID, lock: Boolean = false): ListingDto? {
        // Lock only inventory, not the shared category row joined by the projection.
        if (lock && Listings.select(Listings.id).where { Listings.id eq id }.forUpdate().singleOrNull() == null) return null
        return listingProjection().where { Listings.id eq id }.singleOrNull()?.let(::listingDto)
    }
    private fun listingDto(row: ResultRow) = ListingDto(
        row[Listings.id].value.toString(), row[Listings.supplierId].value.toString(), row[Listings.categoryId].value.toString(),
        row[Listings.title], row[Listings.description], row[Listings.totalKg], row[Listings.availableKg], row[Listings.district], row[Listings.status],
        ListingPhotos.selectAll().where { ListingPhotos.listingId eq row[Listings.id] }.orderBy(ListingPhotos.sortOrder).map { it[ListingPhotos.imageUrl] },
        categoryName = row[Categories.name],
        supplierName = row[listingSupplier[Users.name]]
    )
    fun listings(categoryId: UUID?, district: String?, q: String?, page: Int, limit: Int): ListingPage {
        val query = listingProjection().where { Listings.status eq "ACTIVE" }
        categoryId?.let { value -> query.andWhere { Listings.categoryId eq value } }
        district?.let { value -> query.andWhere { Listings.district eq value } }
        q?.let { value ->
            val escaped = value.lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            query.andWhere { (Listings.title.lowerCase() like "%$escaped%") or (Listings.description.lowerCase() like "%$escaped%") }
        }
        val total = query.count()
        return ListingPage(query.orderBy(Listings.id).limit(limit).offset((page.toLong() - 1) * limit).map(::listingDto), page, limit, total)
    }
    fun insertListing(supplier: UUID, request: ListingCreate): ListingDto {
        val id = Listings.insertAndGetId {
            it[supplierId] = supplier; it[categoryId] = UUID.fromString(request.categoryId)
            it[title] = request.title; it[description] = request.description; it[totalKg] = request.totalKg; it[availableKg] = request.totalKg
            it[district] = request.district; it[status] = "ACTIVE"
        }.value
        replacePhotos(id, request.photos)
        return listing(id)!!
    }
    fun updateListing(id: UUID, patch: ListingPatch, available: BigDecimal): ListingDto {
        Listings.update({ Listings.id eq id }) {
            patch.categoryId?.let { v -> it[categoryId] = UUID.fromString(v) }; patch.title?.let { v -> it[title] = v }
            patch.description?.let { v -> it[description] = v }; patch.totalKg?.let { v -> it[totalKg] = v }
            patch.district?.let { v -> it[district] = v }; patch.status?.let { v -> it[status] = v }
            it[availableKg] = available
        }
        patch.photos?.let { replacePhotos(id, it) }
        return listing(id)!!
    }
    private fun replacePhotos(id: UUID, urls: List<String>) {
        ListingPhotos.deleteWhere { listingId eq id }
        urls.forEachIndexed { index, url -> ListingPhotos.insert { it[listingId] = id; it[imageUrl] = url; it[sortOrder] = index } }
    }
    fun stock(id: UUID, available: BigDecimal) { Listings.update({ Listings.id eq id }) { it[availableKg] = available } }
    private val reservationSupplier = Users.alias("reservation_supplier")
    private val reservationReceiver = Users.alias("reservation_receiver")
    private fun reservationProjection() = (Reservations innerJoin Listings innerJoin Categories)
        .join(reservationSupplier, JoinType.INNER, Listings.supplierId, reservationSupplier[Users.id])
        .join(reservationReceiver, JoinType.INNER, Reservations.receiverId, reservationReceiver[Users.id])
        .join(Transfers, JoinType.LEFT, Reservations.id, Transfers.reservationId)
        .select(Reservations.columns + listOf(
            Listings.supplierId, Listings.title, Listings.district, Categories.name,
            reservationSupplier[Users.name], reservationReceiver[Users.name], Transfers.id, Transfers.actualKg, Transfers.completedAt
        ))
    fun reservationListingId(id: UUID): UUID? = Reservations.select(Reservations.listingId)
        .where { Reservations.id eq id }.singleOrNull()?.get(Reservations.listingId)?.value
    fun reservation(id: UUID, lock: Boolean = false): ReservationDto? {
        // A separate base-table lock avoids locking users/categories and PostgreSQL's
        // restriction on FOR UPDATE over the nullable side of a LEFT JOIN (transfers).
        if (lock && Reservations.select(Reservations.id).where { Reservations.id eq id }.forUpdate().singleOrNull() == null) return null
        return reservationProjection().where { Reservations.id eq id }.singleOrNull()?.let(::reservationDto)
    }
    private fun reservationDto(row: ResultRow) = ReservationDto(
        id = row[Reservations.id].value.toString(),
        listingId = row[Reservations.listingId].value.toString(),
        receiverId = row[Reservations.receiverId].value.toString(),
        supplierId = row[Listings.supplierId].value.toString(),
        requestedKg = row[Reservations.requestedKg],
        reservedKg = row[Reservations.reservedKg],
        status = row[Reservations.status],
        createdAt = row[Reservations.createdAt].iso(),
        updatedAt = row[Reservations.updatedAt].iso(),
        transferId = row.getOrNull(Transfers.id)?.value?.toString(),
        listingTitle = row[Listings.title],
        categoryName = row[Categories.name],
        listingDistrict = row[Listings.district],
        supplierName = row[reservationSupplier[Users.name]],
        receiverName = row[reservationReceiver[Users.name]],
        actualKg = row.getOrNull(Transfers.actualKg),
        completedAt = row.getOrNull(Transfers.completedAt)?.iso()
    )
    fun reservations(user: UUID, incoming: Boolean): List<ReservationDto> {
        val query = reservationProjection()
        if (incoming) query.where { Listings.supplierId eq user } else query.where { Reservations.receiverId eq user }
        return query.orderBy(Reservations.createdAt, SortOrder.DESC).map(::reservationDto)
    }
    fun insertReservation(receiver: UUID, listing: UUID, requested: BigDecimal): ReservationDto {
        val now = System.currentTimeMillis()
        val id = Reservations.insertAndGetId {
            it[listingId] = listing; it[receiverId] = receiver; it[requestedKg] = requested; it[reservedKg] = BigDecimal.ZERO
            it[status] = "REQUESTED"; it[createdAt] = now; it[updatedAt] = now
        }.value
        return reservation(id)!!
    }
    fun reservationState(id: UUID, state: String, reserved: BigDecimal? = null): ReservationDto {
        Reservations.update({ Reservations.id eq id }) {
            it[status] = state; it[updatedAt] = System.currentTimeMillis(); reserved?.let { v -> it[reservedKg] = v }
        }
        return reservation(id)!!
    }
    fun token(id: UUID): TokenRecord? = HandoverTokens.selectAll().where { HandoverTokens.reservationId eq id }.singleOrNull()?.let { TokenRecord(it[HandoverTokens.tokenHash], it[HandoverTokens.expiresAt], it[HandoverTokens.usedAt]) }
    fun saveToken(id: UUID, hash: String, expiry: Long) {
        if (token(id) == null) HandoverTokens.insert { it[reservationId] = id; it[tokenHash] = hash; it[expiresAt] = expiry; it[usedAt] = null }
        else HandoverTokens.update({ HandoverTokens.reservationId eq id }) { it[tokenHash] = hash; it[expiresAt] = expiry; it[usedAt] = null }
    }
    fun consumeToken(id: UUID) { HandoverTokens.update({ HandoverTokens.reservationId eq id }) { it[usedAt] = System.currentTimeMillis() } }
    fun insertTransfer(id: UUID, actual: BigDecimal): TransferDto {
        val now = System.currentTimeMillis()
        val transferId = Transfers.insertAndGetId { it[reservationId] = id; it[actualKg] = actual; it[completedAt] = now }.value
        return TransferDto(transferId.toString(), id.toString(), actual, now.iso())
    }
    fun transferReservation(id: UUID): UUID? = Transfers.selectAll().where { Transfers.id eq id }.singleOrNull()?.get(Transfers.reservationId)?.value
    fun impact(user: UUID): ImpactDto {
        val joined = Transfers innerJoin Reservations innerJoin Listings
        val sum = Transfers.actualKg.sum()
        val count = Transfers.id.count()
        fun aggregate(filter: Op<Boolean>): Pair<BigDecimal, Long> {
            val row = joined.select(sum, count).where { (Reservations.status eq "COMPLETED") and filter }.single()
            return (row[sum] ?: BigDecimal("0.00")) to row[count]
        }
        val supplied = Listings.supplierId eq user
        val received = Reservations.receiverId eq user
        val total = aggregate(supplied or received)
        return ImpactDto(total.first, total.second, aggregate(supplied).first, aggregate(received).first)
    }
    fun saveFeedback(transfer: UUID, status: String): FeedbackDto {
        val now = System.currentTimeMillis()
        val existing = ReuseFeedback.selectAll().where { ReuseFeedback.transferId eq transfer }.singleOrNull()?.get(ReuseFeedback.id)?.value
        val id = existing ?: ReuseFeedback.insertAndGetId { it[transferId] = transfer; it[ReuseFeedback.status] = status; it[reportedAt] = now }.value
        if (existing != null) ReuseFeedback.update({ ReuseFeedback.id eq id }) { it[ReuseFeedback.status] = status; it[reportedAt] = now }
        return FeedbackDto(id.toString(), transfer.toString(), status, now.iso())
    }
}
