package uk.scimone.diafit.core.data.networking.util

fun constructUrl(url: String, baseUrl: String, apiKey: String?): String {
    val trimmedBaseUrl = baseUrl.trimEnd('/')  // remove trailing slash if any
    val path = url.trimStart('/')              // remove leading slash if any

    val base = if (url.contains(trimmedBaseUrl)) {
        url
    } else {
        "$trimmedBaseUrl/$path"  // always exactly one slash between baseUrl and path
    }

    return if (!apiKey.isNullOrBlank()) {
        if (base.contains("?")) "$base&apiKey=$apiKey"
        else "$base?apiKey=$apiKey"
    } else {
        base
    }
}
