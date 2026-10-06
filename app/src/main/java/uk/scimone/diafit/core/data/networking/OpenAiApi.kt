package uk.scimone.diafit.core.data.networking

import io.ktor.client.HttpClient
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

/** Minimal client for the OpenAI-compatible `/chat/completions` endpoint, vision-capable. */
class OpenAiApi(private val client: HttpClient) {

    suspend fun analyzeMealPhoto(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        imageBase64: String,
        imageMimeType: String
    ): Result<ChatCompletionResponseDto, NetworkError> {
        val url = baseUrl.trimEnd('/') + "/chat/completions"

        val requestBody = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", JsonArray(listOf(
                        buildJsonObject {
                            put("type", "text")
                            put("text", prompt)
                        },
                        buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", JsonPrimitive("data:$imageMimeType;base64,$imageBase64"))
                            })
                        }
                    )))
                }
            )))
            put("response_format", buildJsonObject {
                put("type", "json_object")
            })
        }

        return safeCall {
            client.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
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
