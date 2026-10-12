package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatCompletionChoiceDto> = emptyList(),
    val usage: ChatCompletionUsageDto? = null
)

@Serializable
data class ChatCompletionUsageDto(
    @kotlinx.serialization.SerialName("prompt_tokens") val promptTokens: Int? = null,
    @kotlinx.serialization.SerialName("completion_tokens") val completionTokens: Int? = null
)

@Serializable
data class ChatCompletionChoiceDto(
    val message: ChatCompletionMessageDto
)

@Serializable
data class ChatCompletionMessageDto(
    val content: String? = null
)

@Serializable
data class ModelListResponseDto(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String
)
