package app.verbatim

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class Reminder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isAlarmTick = action == ACTION_ALARM_TICK
        val isSystemReschedule =
            action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_TIMEZONE_CHANGED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isAlarmTick && !isSystemReschedule) return

        val library = runCatching { EntryStore(context).read() }.getOrNull() ?: return
        if (isAlarmTick) {
            notifyIfDue(context, library)
        }
        schedule(context, library)
    }

    companion object {
        private const val ACTION_ALARM_TICK = "app.verbatim.android.REMINDER_TICK"
        private const val CHANNEL_ID = "reviews"
        private const val NOTIFICATION_ID = 1
        private const val ALARM_WINDOW_MS = 15 * 60_000L

        private fun ensureChannel(notifications: NotificationManager) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Due reviews",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        private fun canPostNotifications(c: Context, notifications: NotificationManager): Boolean =
            notifications.areNotificationsEnabled() &&
                (Build.VERSION.SDK_INT < 33 ||
                    c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED)

        private fun postNotification(c: Context, notifications: NotificationManager, due: Int) {
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open =
                PendingIntent.getActivity(
                    c,
                    1,
                    Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    flags,
                )
            notifications.notify(
                NOTIFICATION_ID,
                Notification.Builder(c, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_verbatim)
                    .setContentTitle("Time to recite")
                    .setContentText("Due for review: $due")
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build(),
            )
        }

        /**
         * Updates the next alarm and keeps any currently visible notification count accurate,
         * without ever popping up a brand-new notification while the user is inside the app.
         */
        fun schedule(c: Context, library: Library) {
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()
            val dates = library.entries.mapNotNull { it.review?.day }
            val due = dates.count { it <= today }
            val alarm = c.getSystemService(AlarmManager::class.java)
            val notifications = c.getSystemService(NotificationManager::class.java)
            ensureChannel(notifications)

            if (due == 0) {
                notifications.cancel(NOTIFICATION_ID)
            } else {
                val isCurrentlyShowing =
                    notifications.activeNotifications.any { it.id == NOTIFICATION_ID }
                if (isCurrentlyShowing && canPostNotifications(c, notifications)) {
                    postNotification(c, notifications, due)
                }
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val tick =
                PendingIntent.getBroadcast(
                    c,
                    0,
                    Intent(c, Reminder::class.java).setAction(ACTION_ALARM_TICK),
                    flags,
                )
            val first = dates.minOrNull()
            if (first == null) {
                alarm.cancel(tick)
            } else {
                val time = LocalTime.of(library.hour, library.minute)
                val afterTime = now.toLocalTime() >= time
                val day = maxOf(first, if (afterTime) today.plusDays(1) else today)
                val triggerAt = day.atTime(time).atZone(now.zone).toInstant().toEpochMilli()
                alarm.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, ALARM_WINDOW_MS, tick)
            }
        }

        /** Called only when the scheduled daily alarm fires. */
        private fun notifyIfDue(c: Context, library: Library) {
            val today = LocalDate.now()
            val due = library.entries.count { it.review?.due(today) == true }
            val notifications = c.getSystemService(NotificationManager::class.java)
            ensureChannel(notifications)
            if (due == 0) {
                notifications.cancel(NOTIFICATION_ID)
                return
            }
            val prefs = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            if (
                prefs.getString("lastDay", null) != today.toString() &&
                    canPostNotifications(c, notifications)
            ) {
                postNotification(c, notifications, due)
                prefs.edit { putString("lastDay", today.toString()) }
            }
        }
    }
}
