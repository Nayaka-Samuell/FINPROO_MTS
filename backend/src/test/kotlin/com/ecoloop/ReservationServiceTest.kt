package com.ecoloop

import com.ecoloop.dtos.*
import com.ecoloop.models.HandoverTokens
import com.ecoloop.plugins.DatabaseFactory
import com.ecoloop.repositories.ExposedEcoRepository
import com.ecoloop.services.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*

class ReservationServiceTest {
    private fun kg(value: String) = BigDecimal(value)
    private suspend fun fixture(block: suspend Fixture.() -> Unit) {
        val db = DatabaseFactory.connect(mapOf("DB_URL" to "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_UPPER=false"))
        try {
            val repository = ExposedEcoRepository(db.database)
            val users = repository.transaction {
                (1..3).map { insertUser("User $it", "user$it@example.com", "not-used-in-service-tests", "DKI Jakarta", "Jakarta Selatan") }
            }
            val catalog = CatalogService(repository)
            val listing = catalog.create(UUID.fromString(users[0].id), ListingCreate("00000000-0000-0000-0000-000000000001", "Kain perca", "Bersih dan non-B3", kg("10.00"), "Jakarta Selatan"))
            Fixture(db.database, repository, catalog, ReservationService(repository), users.map { UUID.fromString(it.id) }, UUID.fromString(listing.id)).block()
        } finally { db.close() }
    }
    private inner class Fixture(
        val database: org.jetbrains.exposed.sql.Database,
        val repository: ExposedEcoRepository,
        val catalog: CatalogService,
        val service: ReservationService,
        val users: List<UUID>,
        val listing: UUID
    ) {
        val supplier get() = users[0]
        val receiver get() = users[1]
        val stranger get() = users[2]
        suspend fun request(amount: String = "4.00", user: UUID = receiver) = UUID.fromString(service.create(user, ReservationCreate(listing.toString(), kg(amount))).id)
        suspend fun stock() = catalog.detail(listing).availableKg
        fun assertProjection(reservation: ReservationDto) {
            assertEquals(listing.toString(), reservation.listingId)
            assertEquals(supplier.toString(), reservation.supplierId)
            assertEquals(receiver.toString(), reservation.receiverId)
            assertEquals("Kain perca", reservation.listingTitle)
            assertEquals("Kain Perca", reservation.categoryName)
            assertEquals("Jakarta Selatan", reservation.listingDistrict)
            assertEquals("User 1", reservation.supplierName)
            assertEquals("User 2", reservation.receiverName)
        }
        suspend fun expect(code: String, block: suspend () -> Unit) {
            val error = assertFailsWith<ApiException> { block() }
            assertEquals(code, error.code)
        }
    }

    @Test fun `request approval smaller actual and once-only completion`() = runBlocking {
        fixture {
            val id = request()
            assertEquals(kg("10.00"), stock())
            assertEquals(0L, service.impact(supplier).transferCount)
            service.approve(supplier, id)
            assertEquals(kg("6.00"), stock())
            assertEquals(kg("0.00"), service.impact(supplier).totalKg)
            val token = service.handover(receiver, id)
            val stored = repository.transaction { token(id) }!!
            assertNotEquals(token.token, stored.hash)
            assertEquals(64, stored.hash.length)
            val transfer = service.complete(supplier, id, CompleteRequest(token.token, kg("3.80")))
            assertEquals(kg("6.20"), stock())
            assertEquals(kg("3.80"), service.impact(supplier).totalKg)
            assertEquals(1L, service.impact(receiver).transferCount)
            assertEquals(kg("0.00"), service.impact(stranger).totalKg)
            expect("INVALID_STATE") { service.complete(supplier, id, CompleteRequest(token.token, kg("3.80"))) }
            assertEquals(1L, service.impact(supplier).transferCount)
            expect("FORBIDDEN") { service.feedback(supplier, UUID.fromString(transfer.id), FeedbackRequest("USED")) }
            assertEquals("USED", service.feedback(receiver, UUID.fromString(transfer.id), FeedbackRequest("USED")).status)
            assertEquals("STORED", service.feedback(receiver, UUID.fromString(transfer.id), FeedbackRequest("STORED")).status)
        }
    }

    @Test fun `reservation projections are populated across the complete lifecycle`() = runBlocking {
        fixture {
            val created = service.create(receiver, ReservationCreate(listing.toString(), kg("4.00")))
            assertProjection(created)
            assertNull(created.transferId)
            val id = UUID.fromString(created.id)
            assertProjection(service.list(supplier, "incoming").single())
            assertProjection(service.list(receiver, "outgoing").single())
            assertTrue(service.list(stranger, "incoming").isEmpty())
            assertProjection(service.approve(supplier, id))
            val token = service.handover(receiver, id).token
            assertProjection(repository.transaction { reservation(id)!! })
            val transfer = service.complete(supplier, id, CompleteRequest(token, kg("3.80")))
            val completed = service.list(receiver, "outgoing").single()
            assertProjection(completed)
            assertEquals("COMPLETED", completed.status)
            assertEquals(transfer.id, completed.transferId)

            val rejected = service.reject(supplier, request())
            assertProjection(rejected)
            assertEquals("REJECTED", rejected.status)
            val cancelledId = request()
            assertProjection(service.approve(supplier, cancelledId))
            val cancelled = service.cancel(receiver, cancelledId)
            assertProjection(cancelled)
            assertEquals("CANCELLED", cancelled.status)
            service.list(supplier, "incoming").forEach(::assertProjection)
        }
    }

    @Test fun `projections reflect current listing category location and participant names`() = runBlocking {
        fixture {
            val id = request()
            val patched = catalog.patch(supplier, listing, ListingPatch(
                categoryId = "00000000-0000-0000-0000-000000000002",
                title = "Kardus untuk kerajinan",
                district = "Jakarta Barat"
            ))
            assertEquals("Kardus Bersih", patched.categoryName)
            assertEquals("Kardus Bersih", catalog.detail(listing).categoryName)
            repository.transaction {
                updateProfile(supplier, ProfilePatch(name = "UMKM Kardus"))
                updateProfile(receiver, ProfilePatch(name = "Komunitas Kreatif"))
            }
            val projected = service.list(receiver, "outgoing").single()
            assertEquals("Kardus untuk kerajinan", projected.listingTitle)
            assertEquals("Kardus Bersih", projected.categoryName)
            assertEquals("Jakarta Barat", projected.listingDistrict)
            assertEquals("UMKM Kardus", projected.supplierName)
            assertEquals("Komunitas Kreatif", projected.receiverName)
            val approved = service.approve(supplier, id)
            assertEquals(projected.listingTitle, approved.listingTitle)
            assertEquals(projected.categoryName, approved.categoryName)
            assertEquals(projected.supplierName, approved.supplierName)
            assertEquals(projected.receiverName, approved.receiverName)
        }
    }

    @Test fun `concurrent approvals cannot overbook`() = runBlocking {
        fixture {
            val first = request("7.00")
            val second = request("7.00", stranger)
            val outcomes = coroutineScope {
                listOf(first, second).map { id -> async(Dispatchers.IO) { runCatching { service.approve(supplier, id) } } }.awaitAll()
            }
            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals("INSUFFICIENT_STOCK", (outcomes.single { it.isFailure }.exceptionOrNull() as ApiException).code)
            assertEquals(kg("3.00"), stock())
        }
    }

    @Test fun `concurrent double completion produces one transfer`() = runBlocking {
        fixture {
            val id = request()
            service.approve(supplier, id)
            val token = service.handover(receiver, id).token
            val outcomes = coroutineScope {
                (1..2).map { async(Dispatchers.IO) { runCatching { service.complete(supplier, id, CompleteRequest(token, kg("4.00"))) } } }.awaitAll()
            }
            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals(1L, service.impact(receiver).transferCount)
            assertEquals(kg("6.00"), stock())
        }
    }

    @Test fun `cancel and rejection release allocated stock exactly once`() = runBlocking {
        fixture {
            val id = request()
            service.approve(supplier, id)
            service.cancel(receiver, id)
            assertEquals(kg("10.00"), stock())
            expect("INVALID_STATE") { service.cancel(receiver, id) }
            val second = request()
            service.approve(supplier, second)
            service.reject(supplier, second)
            assertEquals(kg("10.00"), stock())
            assertEquals(0L, service.impact(supplier).transferCount)
        }
    }

    @Test fun `authorization and invalid transitions do not change stock`() = runBlocking {
        fixture {
            expect("SELF_RESERVATION") { request(user = supplier) }
            val id = request()
            expect("FORBIDDEN") { service.approve(receiver, id) }
            expect("FORBIDDEN") { service.reject(stranger, id) }
            expect("FORBIDDEN") { service.cancel(stranger, id) }
            expect("INVALID_STATE") { service.handover(receiver, id) }
            expect("FORBIDDEN") { catalog.patch(stranger, listing, ListingPatch(title = "Changed")) }
            service.approve(supplier, id)
            expect("INVALID_STATE") { service.approve(supplier, id) }
            expect("FORBIDDEN") { service.handover(supplier, id) }
            val token = service.handover(receiver, id).token
            expect("FORBIDDEN") { service.complete(receiver, id, CompleteRequest(token, kg("4.00"))) }
            expect("INVALID_HANDOVER_TOKEN") { service.complete(supplier, id, CompleteRequest("x".repeat(43), kg("4.00"))) }
            assertEquals(kg("6.00"), stock())
        }
    }

    @Test fun `expired rotated and cancelled tokens cannot complete`() = runBlocking {
        fixture {
            val id = request()
            service.approve(supplier, id)
            val old = service.handover(receiver, id).token
            val current = service.handover(receiver, id).token
            expect("INVALID_HANDOVER_TOKEN") { service.complete(supplier, id, CompleteRequest(old, kg("4.00"))) }
            transaction(database) { HandoverTokens.update({ HandoverTokens.reservationId eq id }) { it[expiresAt] = System.currentTimeMillis() - 1 } }
            expect("TOKEN_EXPIRED") { service.complete(supplier, id, CompleteRequest(current, kg("4.00"))) }
            val fresh = service.handover(receiver, id).token
            service.cancel(receiver, id)
            expect("INVALID_STATE") { service.complete(supplier, id, CompleteRequest(fresh, kg("4.00"))) }
            assertEquals(kg("10.00"), stock())
        }
    }

    @Test fun `higher actual requires extra available stock and failed completion rolls back`() = runBlocking {
        fixture {
            val first = request("4.00")
            service.approve(supplier, first)
            val second = request("5.00", stranger)
            service.approve(supplier, second)
            val token = service.handover(receiver, first).token
            expect("INSUFFICIENT_STOCK") { service.complete(supplier, first, CompleteRequest(token, kg("6.00"))) }
            assertEquals(kg("1.00"), stock())
            assertNull(repository.transaction { token(first) }!!.usedAt)
            service.complete(supplier, first, CompleteRequest(token, kg("5.00")))
            assertEquals(kg("0.00"), stock())
        }
    }

    @Test fun `listing edits preserve allocations and validation enforces decimal and region rules`() = runBlocking {
        fixture {
            expect("VALIDATION_ERROR") { request("0.00") }
            expect("VALIDATION_ERROR") { request("1.001") }
            val id = request("7.00")
            service.approve(supplier, id)
            expect("STOCK_ALLOCATED") { catalog.patch(supplier, listing, ListingPatch(totalKg = kg("6.00"))) }
            catalog.patch(supplier, listing, ListingPatch(totalKg = kg("12.00")))
            assertEquals(kg("5.00"), stock())
            expect("VALIDATION_ERROR") { catalog.patch(supplier, listing, ListingPatch(district = "Malang")) }
            assertEquals(1L, catalog.browse(null, "Jakarta Selatan", "perca", "1", "20").total)
            catalog.delete(supplier, listing)
            assertEquals(0L, catalog.browse(null, null, null, null, null).total)
            expect("LISTING_INACTIVE") { request() }
            service.cancel(receiver, id)
            assertEquals(kg("12.00"), stock())
        }
    }
}
