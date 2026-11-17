package com.ecoloop.app.data.remote

import com.ecoloop.app.BuildConfig
import com.ecoloop.app.data.local.SessionStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Menambahkan header Authorization dari token di memori, dan mengakhiri sesi
 * ketika server menolak token (401). Request tanpa token (mis. login gagal)
 * tidak memicu logout.
 */
private class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.token
        val request = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()
        val response = chain.proceed(request)
        if (response.code == 401 && !token.isNullOrBlank()) session.expire()
        return response
    }
}

object ApiClient {
    fun create(session: SessionStore, baseUrl: String = BuildConfig.BASE_URL): EcoApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EcoApi::class.java)
    }
}
