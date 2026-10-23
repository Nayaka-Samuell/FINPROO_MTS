package com.ecoloop.services

import com.ecoloop.dtos.*
import com.ecoloop.repositories.EcoRepository
import java.util.UUID

class CatalogService(private val repository: EcoRepository) {
    suspend fun categories() = repository.transaction { categories() }
    suspend fun browse(category: String?, area: String?, q: String?, page: String?, limit: String?): ListingPage {
        val categoryId = category?.let(::uuid)
        val district = area?.let(::district)
        val search = q?.let { text(it, "Pencarian", 160) }
        val pageNumber = page?.let { it.toIntOrNull() ?: invalid("Page tidak valid") } ?: 1
        val pageSize = limit?.let { it.toIntOrNull() ?: invalid("Limit tidak valid") } ?: 20
        if (pageNumber < 1 || pageSize !in 1..100) invalid("Page minimal 1 dan limit 1–100")
        return repository.transaction { listings(categoryId, district, search, pageNumber, pageSize) }
    }
    suspend fun detail(id: UUID) = repository.transaction { listing(id) ?: missing("Listing") }
    suspend fun create(user: UUID, request: ListingCreate): ListingDto {
        val category = uuid(request.categoryId)
        val cleaned = request.copy(title = text(request.title, "Judul", 160), description = text(request.description, "Deskripsi", 5000), totalKg = weight(request.totalKg), district = district(request.district), photos = photos(request.photos))
        return repository.transaction {
            if (!categoryExists(category)) invalid("Kategori tidak ditemukan")
            insertListing(user, cleaned)
        }
    }
    suspend fun patch(user: UUID, id: UUID, request: ListingPatch): ListingDto {
        val category = request.categoryId?.let(::uuid)
        val patch = request.copy(title = request.title?.let { text(it, "Judul", 160) }, description = request.description?.let { text(it, "Deskripsi", 5000) }, totalKg = request.totalKg?.let(::weight), district = request.district?.let(::district), photos = request.photos?.let(::photos))
        if (patch == ListingPatch()) invalid("Tidak ada perubahan listing")
        if (patch.status != null && patch.status !in setOf("ACTIVE", "INACTIVE", "COMPLETED")) invalid("Status listing tidak valid")
        return repository.transaction {
            val current = listing(id, lock = true) ?: missing("Listing")
            if (current.supplierId != user.toString()) forbidden()
            if (category != null && !categoryExists(category)) invalid("Kategori tidak ditemukan")
            val available = current.availableKg + ((patch.totalKg ?: current.totalKg) - current.totalKg)
            if (available.signum() < 0) conflict("STOCK_ALLOCATED", "Total tidak boleh lebih kecil dari stok yang sudah dialokasikan/disalurkan")
            if (patch.status == "COMPLETED" && available.signum() != 0) conflict("STOCK_REMAINING", "Listing masih memiliki stok tersedia")
            updateListing(id, patch, available)
        }
    }
    suspend fun delete(user: UUID, id: UUID) = patch(user, id, ListingPatch(status = "INACTIVE"))
}
