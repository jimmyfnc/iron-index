package com.ironindex.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Rest timer outside the app: a silent live countdown in the notification shade and a "Rest's up" alert when
 * it ends. The alert uses an exact alarm when the phone allows it (Settings → Alarms & reminders); otherwise
 * an ordinary alarm, which Android may deliver noticeably late. The countdown itself is always exact and keeps
 * showing (counting past zero) until the alert replaces it.
 */
object RestAlarm {
    private const val COUNTDOWN_CHANNEL = "rest_countdown"
    private const val DONE_CHANNEL = "rest_done"
    private const val COUNTDOWN_ID = 41
    private const val DONE_ID = 42
    const val EXTRA_LABEL = "label"

    fun canNotify(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether the alert can fire on the second (Settings → Alarms & reminders), not up to a minute late. */
    fun onTime(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Opens IronIndex's Alarms & reminders switch. */
    @androidx.annotation.RequiresApi(31)
    fun onTimeSettings(context: Context) = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())

    // Offered once: someone who leaves it off shouldn't see the bell on every rest.
    private fun prefs(context: Context) = context.getSharedPreferences("rest_alarm", Context.MODE_PRIVATE)
    fun askedOnTime(context: Context) = prefs(context).getBoolean("askedOnTime", false)
    fun markAskedOnTime(context: Context) = prefs(context).edit { putBoolean("askedOnTime", true) }

    private fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(COUNTDOWN_CHANNEL, "Rest countdown", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "The rest timer while you're outside IronIndex" })
        manager.createNotificationChannel(NotificationChannel(DONE_CHANNEL, "Rest's up", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "When rest between sets is over" })
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    private fun alarmIntent(context: Context, label: String = "") = PendingIntent.getBroadcast(context, 0,
        Intent(context, RestDoneReceiver::class.java).putExtra(EXTRA_LABEL, label), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Shows the countdown and schedules the alert for [timer] (replacing any earlier one). */
    fun schedule(context: Context, timer: RestTimer, now: Long = System.currentTimeMillis()) {
        cancel(context)
        if (!canNotify(context)) return
        channels(context)
        val remaining = (timer.endsAt - now).coerceAtLeast(0)
        val countdown = NotificationCompat.Builder(context, COUNTDOWN_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Resting").setContentText(timer.label)
            .setWhen(timer.endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openApp(context)).build()
        notifyIfAllowed(context, COUNTDOWN_ID, countdown)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = SystemClock.elapsedRealtime() + remaining
        runCatching {
            if (onTime(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent(context, timer.label))
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarmIntent(context, timer.label))
        }
    }

    /** Posts only with notification permission (checked right here, so it can't be skipped). */
    private fun notifyIfAllowed(context: Context, id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        NotificationManagerCompat.from(context).apply { cancel(COUNTDOWN_ID); cancel(DONE_ID) }
    }

    /** Clears the shade once the in-app bar has shown "Rest's up". */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).apply { cancel(COUNTDOWN_ID); cancel(DONE_ID) }

    /** True while the in-app rest bar is on screen with the app visible; it says "Rest's up" and buzzes itself. */
    @Volatile var barShowing = false

    fun done(context: Context, label: String) {
        NotificationManagerCompat.from(context).cancel(COUNTDOWN_ID)
        // The bar in the app already says "Rest's up" and buzzes; don't pop a heads-up over it.
        if (!canNotify(context) || barShowing) return
        channels(context)
        val alert = NotificationCompat.Builder(context, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Rest's up").setContentText(label.ifBlank { "Time for your next set" })
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setTimeoutAfter(5 * 60_000L).setContentIntent(openApp(context)).build()
        notifyIfAllowed(context, DONE_ID, alert)
    }
}

/** Posts the "Rest's up" alert when the rest alarm fires. */
class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.done(context, intent.getStringExtra(RestAlarm.EXTRA_LABEL).orEmpty())
}
