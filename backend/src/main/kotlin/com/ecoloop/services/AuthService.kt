package com.ecoloop.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.ecoloop.dtos.*
import com.ecoloop.repositories.EcoRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID

class JwtConfig(env: Map<String, String> = System.getenv()) {
    val issuer = env["JWT_ISSUER"] ?: "ecoloop"
    val audience = env["JWT_AUDIENCE"] ?: "ecoloop-android"
    private val secret = env["JWT_SECRET"] ?: run {
        require(env["APP_ENV"] != "production") { "JWT_SECRET wajib di production" }
        Base64.getEncoder().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    }
    init { require(secret.length >= 32) { "JWT_SECRET minimal 32 karakter" } }
    val algorithm: Algorithm = Algorithm.HMAC256(secret)
    fun issue(user: UserDto): AuthDto = AuthDto(JWT.create().withIssuer(issuer).withAudience(audience).withSubject(user.id)
        .withIssuedAt(Date()).withExpiresAt(Date(System.currentTimeMillis() + 86_400_000)).sign(algorithm), user)
}
class AuthService(private val repository: EcoRepository, private val jwt: JwtConfig) {
    // Same bcrypt cost for missing accounts to reduce email enumeration via timing.
    private val dummyHash = BCrypt.withDefaults().hashToString(12, "invalid-password".toCharArray())
    suspend fun register(request: RegisterRequest): AuthDto {
        val name = text(request.name, "Nama", 120)
        val email = email(request.email)
        val city = city(request.city)
        val district = district(request.district)
        if (request.password.length < 8 || request.password.toByteArray().size > 72) invalid("Password minimal 8 karakter dan maksimal 72 byte")
        val hash = withContext(Dispatchers.Default) { BCrypt.withDefaults().hashToString(12, request.password.toCharArray()) }
        return repository.transaction {
            if (userByEmail(email) != null) conflict("EMAIL_EXISTS", "Email sudah terdaftar")
            jwt.issue(insertUser(name, email, hash, city, district))
        }
    }
    suspend fun login(request: LoginRequest): AuthDto {
        val email = email(request.email)
        if (request.password.toByteArray().size > 72) invalid("Password terlalu panjang")
        val account = repository.transaction { userByEmail(email) }
        val valid = withContext(Dispatchers.Default) { BCrypt.verifyer().verify(request.password.toCharArray(), account?.passwordHash ?: dummyHash).verified }
        if (account == null || !valid) throw ApiException(HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Email atau password salah")
        return jwt.issue(account.profile)
    }
    suspend fun me(id: UUID) = repository.transaction { user(id)?.profile ?: missing("Pengguna") }
    suspend fun patch(id: UUID, request: ProfilePatch): UserDto {
        val patch = ProfilePatch(request.name?.let { text(it, "Nama", 120) }, request.city?.let(::city), request.district?.let(::district))
        if (patch == ProfilePatch()) invalid("Tidak ada perubahan profil")
        return repository.transaction { user(id) ?: missing("Pengguna"); updateProfile(id, patch) }
    }
}
