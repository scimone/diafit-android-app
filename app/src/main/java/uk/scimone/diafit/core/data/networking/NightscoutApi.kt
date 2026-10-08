package uk.scimone.diafit.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import uk.scimone.diafit.core.domain.util.networking.NetworkError
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.core.data.networking.dto.NightscoutEntryDto
import uk.scimone.diafit.core.data.networking.util.applyNightscoutAuth
import uk.scimone.diafit.core.data.networking.util.constructUrl
import uk.scimone.diafit.core.data.networking.util.safeCall
import uk.scimone.diafit.settings.domain.usecase.GetNightscoutConfigUseCase
import kotlinx.serialization.json.JsonObject
import java.time.Instant

class NightscoutApi(
    private val client: HttpClient,
    private val getNightscoutConfig: GetNightscoutConfigUseCase
) {

    suspend fun getCgmEntries(startDate: String, endDate: String = Instant.now().toString()): Result<List<NightscoutEntryDto>, NetworkError> {
        val config = getNightscoutConfig()
        val url = constructUrl("/api/v1/entries/sgv.json", baseUrl = config.baseUrl, apiKey = null)

        return safeCall {
            client.get(url) {
                applyNightscoutAuth(config.apiKey)
                parameter("find[dateString][\$gte]", startDate)
                parameter("find[dateString][\$lte]", endDate)
                parameter("count", 10000000)
            }
        }
    }

    /** Treatments (boluses, carbs, temp basals, ...) created in [startMs, endMs), as raw documents. */
    suspend fun getTreatments(startMs: Long, endMs: Long): Result<List<JsonObject>, NetworkError> {
        val config = getNightscoutConfig()
        val url = constructUrl("/api/v1/treatments.json", baseUrl = config.baseUrl, apiKey = null)
        return safeCall {
            client.get(url) {
                applyNightscoutAuth(config.apiKey)
                parameter("find[created_at][\$gte]", Instant.ofEpochMilli(startMs).toString())
                parameter("find[created_at][\$lt]", Instant.ofEpochMilli(endMs).toString())
                parameter("count", 20000)
            }
        }
    }

    /** The `profile` collection (the stored insulin profiles), newest first. */
    suspend fun getProfiles(): Result<List<JsonObject>, NetworkError> {
        val config = getNightscoutConfig()
        val url = constructUrl("/api/v1/profile.json", baseUrl = config.baseUrl, apiKey = null)
        return safeCall { client.get(url) { applyNightscoutAuth(config.apiKey) } }
    }

    /** Checks the saved URL and credentials: reachable, and the secret/token accepted (`/api/v1/verifyauth`). */
    suspend fun testConnection(): NightscoutCheck {
        val config = getNightscoutConfig()
        if (config.baseUrl.isBlank()) return NightscoutCheck.Failed("Enter your Nightscout address first.")
        return try {
            val response = client.get(constructUrl("/api/v1/verifyauth", baseUrl = config.baseUrl, apiKey = null)) {
                applyNightscoutAuth(config.apiKey)
            }
            if (response.status.value !in 200..299) return NightscoutCheck.Failed("The server answered ${response.status.value}.")
            val body = response.bodyAsText()
            when {
                body.contains("OK", ignoreCase = false) -> NightscoutCheck.Ok
                config.apiKey.isBlank() -> NightscoutCheck.Failed("Reachable, but this site needs an API secret or access token.")
                else -> NightscoutCheck.Failed("Reachable, but the API secret / token was rejected.")
            }
        } catch (e: Exception) {
            NightscoutCheck.Failed("Couldn't reach the server (${e.javaClass.simpleName}).")
        }
    }
}

sealed interface NightscoutCheck {
    data object Ok : NightscoutCheck
    data class Failed(val message: String) : NightscoutCheck
}
