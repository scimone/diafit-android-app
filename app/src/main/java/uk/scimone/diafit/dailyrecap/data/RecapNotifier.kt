package uk.scimone.diafit.dailyrecap.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.scimone.diafit.MainActivity
import uk.scimone.diafit.R
import uk.scimone.diafit.dailyrecap.domain.DailyRecap
import uk.scimone.diafit.dailyrecap.presentation.RecapImage
import uk.scimone.diafit.notifications.data.AppNotifier

/** Opens the recap page for a day. */
fun recapLink(epochDay: Long) = "recap:$epochDay"
fun parseRecapLink(link: String?): Long? = link?.takeIf { it.startsWith("recap:") }?.removePrefix("recap:")?.toLongOrNull()

/** The recap's lines as text: ✅ went well, ⚠️ went wrong (numbered like the badges on the chart), 👉 to do. */
fun recapLines(recap: DailyRecap): List<String> {
    var n = 0
    fun numbered(prefix: String, text: String) = "$prefix ${RecapImage.badge(++n)} $text"
    return recap.wentWell.map { numbered("✅", it.text) } +
        recap.wentWrong.map { numbered("⚠️", it.text) } +
        recap.doToday.map { "👉 $it" }
}

/** Pushes a finished recap: headline, the chart as the expanded picture and the lines as its caption. */
class RecapNotifier(private val context: Context) {

    fun push(recap: DailyRecap) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.recap_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.recap_channel_desc) }
            )
        }
        val id = NOTIFICATION_BASE + recap.epochDay.toInt()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(AppNotifier.EXTRA_LINK, recapLink(recap.epochDay)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lines = recapLines(recap)
        val summary = lines.joinToString("\n")
        val collapsed = lines.take(2).joinToString("  ")
        val picture = RecapImage.render(recap)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_diafit)
            .setContentTitle(recap.headline)
            .setContentText(collapsed)
            .setLargeIcon(null as android.graphics.Bitmap?)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .setBigContentTitle(recap.headline)
                    .setSummaryText(summary)
            )
            .setAutoCancel(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        const val CHANNEL_ID = "DAILY_RECAP_CHANNEL"
        const val NOTIFICATION_BASE = 7_000_000
    }
}
