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
    private const val CHANNEL = "daily_records"
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun schedule(context: Context) {
        val options = (context.applicationContext as FitnessApplication).comfort.state.value
        val manager = context.getSystemService(AlarmManager::class.java)
        listOf("weight", "training").forEachIndexed { index, type ->
            val pending = PendingIntent.getBroadcast(context, 200 + index,
                Intent(context, ReminderReceiver::class.java).setAction("${context.packageName}.reminder.$type").putExtra("type", type),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.cancel(pending)
            val enabled = if (type == "weight") options.weightReminder else options.trainingReminder
            if (enabled && allowed(context)) {
                val at = nextReminder(ZonedDateTime.now(), if (type == "weight") options.weightHour else options.trainingHour,
                    if (type == "weight") options.weightMinute else options.trainingMinute)
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending)
            }
        }
    }

    fun show(context: Context, type: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "每日记录提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, if (type == "weight") 301 else 302,
            Intent(context, MainActivity::class.java).setAction("open_$type").putExtra("open_action", type)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(if (type == "weight") 301 else 302,
            NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
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
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> null
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
                    if (needed) Reminders.show(context, type)
                }
            } catch (error: Exception) {
                android.util.Log.w("FitnessReminders", "Unable to show daily reminder", error)
            } finally {
                try { Reminders.schedule(context) } finally { result.finish() }
            }
        }
    }
}
