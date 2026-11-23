package app.verbatim

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import java.time.*

class Reminder : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val library = runCatching { EntryStore(context).read() }.getOrNull() ?: return
        refresh(context, library)
    }

    companion object {
        fun refresh(c: Context, library: Library) {
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()
            val dates = library.entries.mapNotNull { it.review?.day }
            val due = dates.count { it <= today }
            val alarm = c.getSystemService(AlarmManager::class.java)
            val notifications = c.getSystemService(NotificationManager::class.java)
            notifications.createNotificationChannel(
                NotificationChannel(
                    "reviews",
                    "Due reviews",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val tick = PendingIntent.getBroadcast(c, 0, Intent(c, Reminder::class.java), flags)
            val prefs = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val time = LocalTime.of(library.hour, library.minute)
            val afterTime = now.toLocalTime() >= time
            if (due == 0) notifications.cancel(1)
            else if (
                afterTime &&
                    prefs.getString("lastDay", null) != today.toString() &&
                    notifications.areNotificationsEnabled() &&
                    (Build.VERSION.SDK_INT < 33 ||
                        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED)
            ) {
                val open =
                    PendingIntent.getActivity(
                        c,
                        1,
                        Intent(c, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        flags,
                    )
                notifications.notify(
                    1,
                    Notification.Builder(c, "reviews")
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("Time to recite")
                        .setContentText("Due for review: $due")
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build(),
                )
                prefs.edit().putString("lastDay", today.toString()).apply()
            }
            val first = dates.minOrNull()
            if (first == null) alarm.cancel(tick)
            else {
                val day = maxOf(first, if (afterTime) today.plusDays(1) else today)
                alarm.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    day.atTime(time).atZone(now.zone).toInstant().toEpochMilli(),
                    tick,
                )
            }
        }
    }
}
