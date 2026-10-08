package uk.scimone.diafit.notifications.domain

/* What a notification opens when tapped (stored in AppNotificationEntity.link, carried by the push intent). */

const val DEVICES_LINK = "devices"
const val PATTERNS_LINK_PREFIX = "patterns:"
private const val LINK_SEPARATOR = "|"

/** Opens the Patterns page with [texts] highlighted. */
fun patternsLink(texts: List<String>): String = PATTERNS_LINK_PREFIX + texts.joinToString(LINK_SEPARATOR)

/** The highlighted pattern texts of a [patternsLink], or null when [link] isn't one. */
fun parsePatternsLink(link: String?): List<String>? {
    if (link == null || !link.startsWith(PATTERNS_LINK_PREFIX)) return null
    return link.removePrefix(PATTERNS_LINK_PREFIX).split(LINK_SEPARATOR).filter { it.isNotEmpty() }
}
