package uk.scimone.diafit.core.data.networking.util

import uk.scimone.diafit.BuildConfig

fun constructUrl(url: String, apiKey: String? = BuildConfig.API_KEY): String {
    val baseUrl = BuildConfig.BASE_URL.trimEnd('/')  // remove trailing slash if any
    val path = url.trimStart('/')                    // remove leading slash if any

    val base = if (url.contains(baseUrl)) {
        url
    } else {
        "$baseUrl/$path"  // always exactly one slash between baseUrl and path
    }

    return if (!apiKey.isNullOrBlank()) {
        if (base.contains("?")) "$base&apiKey=$apiKey"
        else "$base?apiKey=$apiKey"
    } else {
        base
    }
}
