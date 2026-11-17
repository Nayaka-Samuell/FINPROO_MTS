package com.ecoloop.app.data.remote

import com.ecoloop.app.data.remote.dto.AuthResponse
import com.ecoloop.app.data.remote.dto.CategoryDto
import com.ecoloop.app.data.remote.dto.CompleteRequest
import com.ecoloop.app.data.remote.dto.DataResponse
import com.ecoloop.app.data.remote.dto.ImpactDto
import com.ecoloop.app.data.remote.dto.ListingCreateRequest
import com.ecoloop.app.data.remote.dto.ListingDto
import com.ecoloop.app.data.remote.dto.ListingPage
import com.ecoloop.app.data.remote.dto.LoginRequest
import com.ecoloop.app.data.remote.dto.RegisterRequest
import com.ecoloop.app.data.remote.dto.ReservationDto
import com.ecoloop.app.data.remote.dto.ReservationRequest
import com.ecoloop.app.data.remote.dto.TokenDto
import com.ecoloop.app.data.remote.dto.TransferDto
import com.ecoloop.app.data.remote.dto.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Kontrak REST `/api/v1` (lihat backend/README.md). */
interface EcoApi {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): DataResponse<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): DataResponse<AuthResponse>

    @GET("me")
    suspend fun me(): DataResponse<User>

    @GET("categories")
    suspend fun categories(): DataResponse<List<CategoryDto>>

    @GET("listings")
    suspend fun listings(
        @Query("q") q: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("district") district: String? = null,
        @Query("limit") limit: Int = 50,
    ): DataResponse<ListingPage>

    @GET("listings/{id}")
    suspend fun listing(@Path("id") id: String): DataResponse<ListingDto>

    @POST("listings")
    suspend fun createListing(@Body body: ListingCreateRequest): DataResponse<ListingDto>

    @POST("reservations")
    suspend fun reserve(@Body body: ReservationRequest): DataResponse<ReservationDto>

    @GET("reservations")
    suspend fun reservations(@Query("view") view: String): DataResponse<List<ReservationDto>>

    @POST("reservations/{id}/approve")
    suspend fun approve(@Path("id") id: String): DataResponse<ReservationDto>

    @POST("reservations/{id}/reject")
    suspend fun reject(@Path("id") id: String): DataResponse<ReservationDto>

    @POST("reservations/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): DataResponse<ReservationDto>

    @POST("reservations/{id}/handover-token")
    suspend fun handoverToken(@Path("id") id: String): DataResponse<TokenDto>

    @POST("reservations/{id}/complete")
    suspend fun complete(@Path("id") id: String, @Body body: CompleteRequest): DataResponse<TransferDto>

    @GET("impact/me")
    suspend fun impact(): DataResponse<ImpactDto>
}
