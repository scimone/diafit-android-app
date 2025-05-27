package uk.scimone.diafit.core.data.networking

import uk.scimone.diafit.BuildConfig

fun constructUrl(url: String, apiKey: String? = BuildConfig.API_KEY): String {
    val base = when {
        url.contains(BuildConfig.BASE_URL) -> url
        url.startsWith("/") -> BuildConfig.BASE_URL + url.drop(1)
        else -> BuildConfig.BASE_URL + url
    }

    return if (!apiKey.isNullOrBlank()) {
        if (base.contains("?")) "$base&apiKey=$apiKey"
        else "$base?apiKey=$apiKey"
    } else {
        base
    }
}
