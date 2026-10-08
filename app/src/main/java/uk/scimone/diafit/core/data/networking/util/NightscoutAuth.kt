package uk.scimone.diafit.core.data.networking.util

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import java.security.MessageDigest

private val TOKEN_REGEX = Regex("^[A-Za-z0-9_.]+-[0-9a-fA-F]{16}$")
private val SHA1_REGEX = Regex("^[0-9a-fA-F]{40}$")

/**
 * Authenticates a Nightscout request the way the server expects: an access *token* ("subject-16hex")
 * goes in `?token=`, an API secret as its SHA-1 hex in the `api-secret` header (an already-hashed
 * secret is sent as is). A blank secret sends no credentials (public instances).
 */
fun HttpRequestBuilder.applyNightscoutAuth(secret: String?) {
    val s = secret?.trim().orEmpty()
    when {
        s.isEmpty() -> Unit
        TOKEN_REGEX.matches(s) -> parameter("token", s)
        SHA1_REGEX.matches(s) -> header("api-secret", s.lowercase())
        else -> header("api-secret", sha1Hex(s))
    }
}

internal fun sha1Hex(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
