package uk.scimone.diafit.notifications.domain

/* What a notification opens when tapped (stored in AppNotificationEntity.link, carried by the push intent). */

const val DEVICES_LINK = "devices"

/** Opens the Patterns list. */
const val PATTERNS_LINK = "patterns"
private const val PATTERN_LINK_PREFIX = "pattern:"

/* Links of earlier versions (pushes may still sit in the shade): "insights", "insight:<id>", and "patterns:<texts>" of the old AGP alerts. */
private const val LEGACY_LIST_LINK = "insights"
private const val LEGACY_DETAIL_PREFIX = "insight:"
private const val LEGACY_ALERT_PREFIX = "patterns:"

/** Opens one pattern's detail page. */
fun patternLink(id: String): String = PATTERN_LINK_PREFIX + id

/** The pattern id of a [patternLink], or null when [link] isn't one. */
fun parsePatternLink(link: String?): String? = when {
    link == null -> null
    link.startsWith(PATTERN_LINK_PREFIX) -> link.removePrefix(PATTERN_LINK_PREFIX)
    link.startsWith(LEGACY_DETAIL_PREFIX) -> link.removePrefix(LEGACY_DETAIL_PREFIX)
    else -> null
}

/** True for the links that open the Patterns list. */
fun isPatternsListLink(link: String?): Boolean =
    link == PATTERNS_LINK || link == LEGACY_LIST_LINK || link?.startsWith(LEGACY_ALERT_PREFIX) == true
