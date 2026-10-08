package uk.scimone.diafit.backendsync.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import uk.scimone.diafit.BuildConfig
import uk.scimone.diafit.backendsync.domain.BolusIn
import uk.scimone.diafit.backendsync.domain.CgmIn
import uk.scimone.diafit.backendsync.domain.HeartRateIn
import uk.scimone.diafit.backendsync.domain.MealIn
import uk.scimone.diafit.backendsync.domain.MealPatch
import uk.scimone.diafit.backendsync.domain.SleepSessionIn
import uk.scimone.diafit.backendsync.domain.StepsIn
import uk.scimone.diafit.backendsync.domain.isoUtc

@Serializable
data class BackendUser(val id: Int, val username: String, val email: String = "")

@Serializable
data class BulkResult(val received: Int, val inserted: Int, val skipped: Int)

@Serializable
data class BackendMeal(val id: Int, @SerialName("source_id") val sourceId: String? = null)

@Serializable
private data class BackendMealPage(val items: List<BackendMeal>, val count: Int)

sealed interface BackendResult<out T> {
    data class Ok<T>(val data: T) : BackendResult<T>
    /** [status] is the HTTP status, null when the server wasn't reached. */
    data class Failed(val message: String, val status: Int? = null) : BackendResult<Nothing>
}

/**
 * Client for the Diafit backend API v2 (bearer token auth). It has its own HttpClient so the token and the large
 * bulk bodies are never written to Logcat (the shared client logs everything).
 */
class BackendApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
        if (BuildConfig.DEBUG) install(Logging) {
            level = LogLevel.INFO
            logger = Logger.ANDROID
            sanitizeHeader { it == HttpHeaders.Authorization }
        }
    }

    suspend fun me(config: BackendConfig): BackendResult<BackendUser> =
        call(BackendUser.serializer()) { client.get(url(config, "me")) { auth(config) } }

    suspend fun uploadCgm(config: BackendConfig, rows: List<CgmIn>) = bulk(config, "cgm", rows, CgmIn.serializer())
    suspend fun uploadBoluses(config: BackendConfig, rows: List<BolusIn>) = bulk(config, "boluses", rows, BolusIn.serializer())
    suspend fun uploadHeartRates(config: BackendConfig, rows: List<HeartRateIn>) = bulk(config, "heart-rates", rows, HeartRateIn.serializer())
    suspend fun uploadSleepSessions(config: BackendConfig, rows: List<SleepSessionIn>) = bulk(config, "sleep-sessions", rows, SleepSessionIn.serializer())
    suspend fun uploadSteps(config: BackendConfig, rows: List<StepsIn>) = bulk(config, "steps", rows, StepsIn.serializer())
    suspend fun uploadMeals(config: BackendConfig, rows: List<MealIn>) = bulk(config, "meals", rows, MealIn.serializer())

    /** The backend id of the app's meal with [sourceId], looked up around the meal time it was last uploaded with. */
    suspend fun findMeal(config: BackendConfig, sourceId: String, mealTimeUtc: Long): BackendResult<BackendMeal?> {
        val page = call(BackendMealPage.serializer()) {
            client.get(url(config, "meals")) {
                auth(config)
                parameter("start", isoUtc(mealTimeUtc - 60_000))
                parameter("end", isoUtc(mealTimeUtc + 60_000))
                parameter("limit", 100)
            }
        }
        return when (page) {
            is BackendResult.Ok -> BackendResult.Ok(page.data.items.firstOrNull { it.sourceId == sourceId })
            is BackendResult.Failed -> page
        }
    }

    suspend fun updateMeal(config: BackendConfig, id: Int, patch: MealPatch): BackendResult<Unit> =
        call(null) {
            client.patch(url(config, "meals/$id")) {
                auth(config)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(MealPatch.serializer(), patch))
            }
        }

    private suspend fun <T> bulk(config: BackendConfig, path: String, rows: List<T>, serializer: KSerializer<T>): BackendResult<BulkResult> {
        if (rows.isEmpty()) return BackendResult.Ok(BulkResult(0, 0, 0))
        return call(BulkResult.serializer()) {
            client.post(url(config, "$path/bulk")) {
                auth(config)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(ListSerializer(serializer), rows))
            }
        }
    }

    private fun HttpRequestBuilder.auth(config: BackendConfig) = bearerAuth(config.token.trim())

    private fun url(config: BackendConfig, path: String): String = "${apiRoot(config.baseUrl)}/$path"

    /** [deserializer] null = ignore the body (returns Unit). */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> call(deserializer: KSerializer<T>?, execute: suspend () -> HttpResponse): BackendResult<T> {
        val response = try {
            execute()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Request failed", e)
            return BackendResult.Failed("Couldn't reach the server (${e.javaClass.simpleName})")
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val status = response.status.value
        if (status !in 200..299) return BackendResult.Failed(errorMessage(status, text), status)
        if (deserializer == null) return BackendResult.Ok(Unit as T)
        return try {
            BackendResult.Ok(json.decodeFromString(deserializer, text))
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected response: ${text.take(300)}", e)
            BackendResult.Failed("Unexpected response from the server", status)
        }
    }

    private fun errorMessage(status: Int, body: String): String {
        // Errors are {"detail": "..."}; validation errors (422) carry a list there instead.
        val detail = runCatching {
            when (val d = json.parseToJsonElement(body).let { it as JsonObject }["detail"]) {
                is JsonPrimitive -> d.content
                null -> null
                else -> d.toString().take(200)
            }
        }.getOrNull()
        val what = when (status) {
            401 -> "Token rejected"
            403 -> "Not allowed"
            404 -> "Not found (check the address)"
            422 -> "Data rejected"
            in 500..599 -> "Server error"
            else -> "HTTP $status"
        }
        return if (detail.isNullOrBlank()) what else "$what: $detail"
    }

    companion object {
        private const val TAG = "BackendSync"

        /** The user may paste the bare host or the API root; requests always go to `<host>/api/v2/...`. */
        fun apiRoot(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/').removeSuffix("/api/v2").removeSuffix("/api").trimEnd('/')
            return "$trimmed/api/v2"
        }
    }
}
