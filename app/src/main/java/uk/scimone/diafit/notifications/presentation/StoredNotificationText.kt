package uk.scimone.diafit.notifications.presentation

import android.content.Context
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.STORED_DUE
import uk.scimone.diafit.core.domain.model.STORED_EXPIRY
import uk.scimone.diafit.core.domain.model.tidyNotificationText
import uk.scimone.diafit.core.presentation.formatStamp

const val STAMP_SKELETON = "EEEdMMMHm"

/** Title of an expiry alert in the current language: "Sensor expires in 9 h". */
fun expiryTitle(context: Context, kind: DeviceKind, leftHours: Int): String {
    val name = context.getString(kind.labelRes)
    return if (leftHours <= 0) context.getString(R.string.expiry_title_soon, name)
    else context.getString(R.string.expiry_title_hours, name, leftHours)
}

fun expiryText(context: Context, expiresAtUtc: Long): String =
    context.getString(R.string.expiry_text, formatStamp(expiresAtUtc, STAMP_SKELETON))

/** Title and text of a stored notification: language-neutral ones are rendered now, older English ones are shown as stored. */
fun renderNotification(context: Context, title: String, text: String): Pair<String, String> {
    val t = title.split('|')
    if (t.firstOrNull() == STORED_EXPIRY && t.size == 3) {
        val kind = DeviceKind.entries.firstOrNull { it.name == t[1] }
        val hours = t[2].toIntOrNull()
        val due = text.removePrefix("$STORED_DUE|").takeIf { text.startsWith("$STORED_DUE|") }?.toLongOrNull()
        if (kind != null && hours != null && due != null) return expiryTitle(context, kind, hours) to expiryText(context, due)
    }
    return tidyNotificationText(title, text)
}
