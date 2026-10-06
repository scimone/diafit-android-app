package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponseDto(
    val choices: List<ChatCompletionChoiceDto> = emptyList()
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
