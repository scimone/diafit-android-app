package uk.scimone.diafit.notifications.domain

/* What a notification opens when tapped (stored in AppNotificationEntity.link, carried by the push intent). */

const val DEVICES_LINK = "devices"

/** Opens the Insights list. Older pattern notifications (`patterns:...`) open it too. */
const val INSIGHTS_LINK = "insights"
private const val INSIGHT_LINK_PREFIX = "insight:"
private const val LEGACY_PATTERNS_PREFIX = "patterns:"

/** Opens one insight's detail page. */
fun insightLink(id: String): String = INSIGHT_LINK_PREFIX + id

/** The insight id of an [insightLink], or null when [link] isn't one. */
fun parseInsightLink(link: String?): String? = link?.takeIf { it.startsWith(INSIGHT_LINK_PREFIX) }?.removePrefix(INSIGHT_LINK_PREFIX)

/** True for the links that open the Insights list ([INSIGHTS_LINK], or a legacy pattern link). */
fun isInsightsListLink(link: String?): Boolean = link == INSIGHTS_LINK || link?.startsWith(LEGACY_PATTERNS_PREFIX) == true
