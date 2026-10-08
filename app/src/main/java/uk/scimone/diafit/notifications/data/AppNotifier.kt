package uk.scimone.diafit.notifications.data

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
import uk.scimone.diafit.core.data.local.NotificationDao
import uk.scimone.diafit.core.domain.model.AppNotificationEntity

/** A push channel: id, user-visible name and description, importance. */
class NotificationChannelSpec(val id: String, val name: String, val description: String, val importance: Int)

/**
 * Raises an app notification: stores it for the in-app bell and, the first time its key is seen, pushes it to the
 * phone. Tapping the push opens [MainActivity] with the notification's link ([EXTRA_LINK]).
 */
class AppNotifier(private val context: Context, private val dao: NotificationDao) {

    /** Returns true when the notification was new (and so was pushed). */
    suspend fun raise(channel: NotificationChannelSpec, key: String, title: String, text: String, link: String, nowUtc: Long): Boolean {
        val inserted = dao.insert(AppNotificationEntity(timestampUtc = nowUtc, title = title, text = text, dedupeKey = key, link = link))
        if (inserted == -1L) return false
        push(channel, key.hashCode(), title, text, link)
        return true
    }

    /** Takes the push for [key] off the phone's notification shade, if it's still there. */
    fun cancel(key: String) = NotificationManagerCompat.from(context).cancel(key.hashCode())

    private fun push(channel: NotificationChannelSpec, id: Int, title: String, text: String, link: String) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (needsPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channel.id, channel.name, channel.importance).apply {
                    description = channel.description
                })
        }
        // Request code per notification, so each pending intent keeps its own link extra.
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_LINK, link),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_diafit)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        /** Intent extra carrying a notification's link into [MainActivity]. */
        const val EXTRA_LINK = "uk.scimone.diafit.NOTIFICATION_LINK"
    }
}
