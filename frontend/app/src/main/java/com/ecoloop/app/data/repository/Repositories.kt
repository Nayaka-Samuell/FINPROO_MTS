package com.ecoloop.app.data.repository

import com.ecoloop.app.data.local.SessionStore
import com.ecoloop.app.data.remote.EcoApi
import com.ecoloop.app.data.remote.dto.CategoryDto
import com.ecoloop.app.data.remote.dto.CompleteRequest
import com.ecoloop.app.data.remote.dto.ImpactDto
import com.ecoloop.app.data.remote.dto.ListingCreateRequest
import com.ecoloop.app.data.remote.dto.ListingDto
import com.ecoloop.app.data.remote.dto.LoginRequest
import com.ecoloop.app.data.remote.dto.RegisterRequest
import com.ecoloop.app.data.remote.dto.ReservationDto
import com.ecoloop.app.data.remote.dto.ReservationRequest
import com.ecoloop.app.data.remote.dto.TokenDto
import com.ecoloop.app.data.remote.dto.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

const val PILOT_CITY = "DKI Jakarta"

val DISTRICTS = listOf(
    "Jakarta Selatan",
    "Jakarta Pusat",
    "Jakarta Barat",
    "Jakarta Timur",
    "Jakarta Utara",
)

/** Sinyal "data berubah" agar layar lain (Beranda, Aktivitas, Dampak) memuat ulang. */
class DataChanges {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()
    fun notifyChanged() {
        _events.tryEmit(Unit)
    }
}

class AuthRepository(
    private val api: EcoApi,
    private val session: SessionStore,
) {
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    suspend fun login(email: String, password: String) {
        val result = api.login(LoginRequest(email.trim(), password)).data
        _user.value = result.user
        session.save(result.token)
    }

    suspend fun register(name: String, email: String, password: String, district: String) {
        val body = RegisterRequest(name.trim(), email.trim(), password, PILOT_CITY, district)
        val result = api.register(body).data
        _user.value = result.user
        session.save(result.token)
    }

    suspend fun refreshUser(): User = api.me().data.also { _user.value = it }

    suspend fun logout() {
        _user.value = null
        session.clear()
    }
}

class CatalogRepository(
    private val api: EcoApi,
    private val changes: DataChanges,
) {
    private var cachedCategories: List<CategoryDto>? = null

    suspend fun categories(): List<CategoryDto> =
        cachedCategories ?: api.categories().data.also { cachedCategories = it }

    suspend fun listings(q: String?, categoryId: String?, district: String?): List<ListingDto> =
        api.listings(q = q, categoryId = categoryId, district = district).data.items

    suspend fun listing(id: String): ListingDto = api.listing(id).data

    suspend fun create(body: ListingCreateRequest): ListingDto =
        api.createListing(body).data.also { changes.notifyChanged() }
}

class ReservationRepository(
    private val api: EcoApi,
    private val changes: DataChanges,
) {
    suspend fun incoming(): List<ReservationDto> = api.reservations("incoming").data
    suspend fun outgoing(): List<ReservationDto> = api.reservations("outgoing").data

    suspend fun reserve(listingId: String, kg: BigDecimal) {
        api.reserve(ReservationRequest(listingId, kg))
        changes.notifyChanged()
    }

    suspend fun approve(id: String) = mutate { api.approve(id) }
    suspend fun reject(id: String) = mutate { api.reject(id) }
    suspend fun cancel(id: String) = mutate { api.cancel(id) }

    suspend fun handoverToken(id: String): TokenDto = api.handoverToken(id).data

    suspend fun complete(id: String, token: String, actualKg: BigDecimal) = mutate {
        api.complete(id, CompleteRequest(token.trim(), actualKg))
    }

    suspend fun impact(): ImpactDto = api.impact().data

    private suspend fun mutate(block: suspend () -> Unit) {
        block()
        changes.notifyChanged()
    }
}
