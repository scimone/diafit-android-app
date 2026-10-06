package uk.scimone.diafit.settings.domain.model

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String = DEFAULT_AI_MODEL
)

const val DEFAULT_AI_MODEL = "gpt-4o-mini"
