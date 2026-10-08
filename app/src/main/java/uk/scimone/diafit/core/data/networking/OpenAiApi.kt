package uk.scimone.diafit.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.scimone.diafit.core.data.networking.dto.ChatCompletionResponseDto
import uk.scimone.diafit.core.data.networking.dto.ModelListResponseDto
import uk.scimone.diafit.core.data.networking.util.safeCall
import uk.scimone.diafit.core.domain.util.networking.NetworkError
import uk.scimone.diafit.core.domain.util.networking.Result

private const val AI_REQUEST_TIMEOUT_MS = 300_000L

/** Minimal client for the OpenAI-compatible `/chat/completions` endpoint, vision-capable. */
class OpenAiApi(private val client: HttpClient) {

    suspend fun analyzeMealPhoto(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        /** One or more base64 JPEGs, all analysed together in a single request. */
        imagesBase64: List<String>
    ): Result<ChatCompletionResponseDto, NetworkError> {
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val requestBody = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", JsonArray(
                        listOf(buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        }) + imagesBase64.map { image ->
                            buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject {
                                    put("url", JsonPrimitive("data:image/jpeg;base64,$image"))
                                })
                            }
                        }
                    ))
                }
            )))
            // Structured outputs: "json_schema" is accepted by both OpenAI and LM Studio (which rejects
            // the older "json_object"). Mirrors the shape requested in MEAL_ANALYSIS_PROMPT.
            put("response_format", buildJsonObject {
                put("type", "json_schema")
                put("json_schema", buildJsonObject {
                    put("name", "meal_analysis")
                    put("strict", true)
                    put("schema", MEAL_ANALYSIS_SCHEMA)
                })
            })
        }

        return safeCall {
            client.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                // Vision requests with several photos (or a slow local model) can take minutes.
                timeout {
                    requestTimeoutMillis = AI_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = AI_REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = 30_000
                }
                setBody(requestBody.toString())
            }
        }
    }

    suspend fun listModels(baseUrl: String, apiKey: String): Result<ModelListResponseDto, NetworkError> =
        safeCall {
            client.get(baseUrl.trimEnd('/') + "/models") {
                header("Authorization", "Bearer $apiKey")
            }
        }
}

private fun stringProp() = buildJsonObject { put("type", "string") }
private fun numberProp() = buildJsonObject { put("type", "number") }

private fun objectSchema(vararg props: Pair<String, kotlinx.serialization.json.JsonElement>) = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { props.forEach { (k, v) -> put(k, v) } })
    put("required", JsonArray(props.map { JsonPrimitive(it.first) }))
    put("additionalProperties", false)
}

private fun enumProp(vararg values: String) = buildJsonObject {
    put("type", "string")
    put("enum", JsonArray(values.map { JsonPrimitive(it) }))
}

/** Mirrors the response shape described in MEAL_ANALYSIS_PROMPT. */
private val MEAL_ANALYSIS_SCHEMA = objectSchema(
    "meal_name" to stringProp(),
    "components" to buildJsonObject {
        put("type", "array")
        put("items", objectSchema(
            "name" to stringProp(),
            "emoji" to stringProp(),
            "basis" to stringProp(),
            "weight_g" to numberProp(),
            "calories" to numberProp(),
            "carbs_g" to numberProp(),
            "sugar_g" to numberProp(),
            "fiber_g" to numberProp(),
            "protein_g" to numberProp(),
            "fat_g" to numberProp(),
            "confidence" to enumProp("LOW", "MEDIUM", "HIGH")
        ))
    },
    "absorption" to enumProp("SHORT", "MEDIUM", "LONG"),
    "reasoning" to stringProp()
)
