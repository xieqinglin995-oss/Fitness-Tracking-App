package com.xieqinglin.fitness.platform

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.MainActivity
import com.xieqinglin.fitness.R
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminders {
    fun channel(type: String) = if (type == "weight") "daily_weight" else "daily_training"
    fun allowed(context: Context, type: String? = null): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (type == null || context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel(type))?.importance != NotificationManager.IMPORTANCE_NONE)

    private fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val blocked = manager.getNotificationChannel("daily_records")?.importance == NotificationManager.IMPORTANCE_NONE
        listOf("weight" to "称重提醒", "training" to "训练提醒").forEach { (type, label) ->
            if (manager.getNotificationChannel(channel(type)) == null) manager.createNotificationChannel(NotificationChannel(channel(type), label,
                if (blocked) NotificationManager.IMPORTANCE_NONE else NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun pending(context: Context, type: String) = PendingIntent.getBroadcast(context, if (type == "weight") 200 else 201,
        Intent(context, ReminderReceiver::class.java).setAction("${context.packageName}.reminder.$type").putExtra("type", type),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun skipToday(context: Context, type: String) {
        context.getSharedPreferences("reminder_skips", 0).edit().putString(type, LocalDate.now().toString()).remove("snooze_$type").apply()
        context.getSystemService(NotificationManager::class.java).cancel(if (type == "weight") 301 else 302)
        schedule(context)
    }
    fun snooze(context: Context, type: String): Boolean {
        val options = (context.applicationContext as FitnessApplication).comfort.state.value
        val now = ZonedDateTime.now(); val at = now.plusMinutes(30)
        if (!allowed(context, type) || now.toLocalDate() != at.toLocalDate() || now.dayOfWeek.value !in options.reminderDays ||
            !(if (type == "weight") options.weightReminder else options.trainingReminder)) return false
        context.getSharedPreferences("reminder_skips", 0).edit().remove(type).putLong("snooze_$type", at.toInstant().toEpochMilli()).apply()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(context, type))
        return true
    }

    fun schedule(context: Context) {
        channels(context)
        val options = (context.applicationContext as FitnessApplication).comfort.state.value
        val manager = context.getSystemService(AlarmManager::class.java)
        listOf("weight", "training").forEach { type ->
            val pending = pending(context, type)
            manager.cancel(pending)
            val enabled = if (type == "weight") options.weightReminder else options.trainingReminder
            if (enabled && allowed(context, type) && options.reminderDays.isNotEmpty()) {
                var at = nextReminder(ZonedDateTime.now(), if (type == "weight") options.weightHour else options.trainingHour,
                    if (type == "weight") options.weightMinute else options.trainingMinute)
                val skipped = context.getSharedPreferences("reminder_skips", 0).getString(type, null)
                val snoozed = context.getSharedPreferences("reminder_skips", 0).getLong("snooze_$type", 0)
                if (snoozed > System.currentTimeMillis()) {
                    val snoozeAt = java.time.Instant.ofEpochMilli(snoozed).atZone(java.time.ZoneId.systemDefault())
                    if (snoozeAt.toLocalDate() == LocalDate.now()) at = snoozeAt
                }
                while (at.dayOfWeek.value !in options.reminderDays || at.toLocalDate().toString() == skipped) at = at.plusDays(1)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
            }
        }
    }

    fun show(context: Context, type: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        channels(context)
        if (!allowed(context, type)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val pending = PendingIntent.getActivity(context, if (type == "weight") 301 else 302,
            Intent(context, MainActivity::class.java).setAction("open_$type").putExtra("open_action", type)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(if (type == "weight") 301 else 302,
            NotificationCompat.Builder(context, channel(type)).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(if (type == "weight") "花几秒记录今天的体重吧" else "今天有训练安排，按自己的节奏开始")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val type = when (action) {
            "${context.packageName}.reminder.weight" -> "weight"
            "${context.packageName}.reminder.training" -> "training"
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> null
            else -> return
        }
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FitnessApplication
                if (type == "weight" || type == "training") {
                    val options = app.comfort.state.value
                    val data = app.repository.snapshot()
                    val date = LocalDate.now().toString()
                    val needed = if (type == "weight") options.weightReminder && data.weights.none { it.date == date }
                    else options.trainingReminder && TrainingCompletion.eligible(data, date).isNotEmpty()
                    val skip = context.getSharedPreferences("reminder_skips", 0).getString(type, null) == date
                    if (needed && !skip && LocalDate.now().dayOfWeek.value in options.reminderDays) Reminders.show(context, type)
                }
                FitnessWidget.refresh(context)
            } catch (error: Exception) {
                android.util.Log.w("FitnessReminders", "Unable to show daily reminder", error)
            } finally {
                try { Reminders.schedule(context) } finally { result.finish() }
            }
        }
    }
}
