package com.xieqinglin.fitness.platform

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderTest {
    @Test fun notificationUsesAChannelAndAnExplicitAppEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(Reminders.allowed(context))
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            Reminders.show(context, "weight")
            val until = SystemClock.elapsedRealtime() + 3_000
            while (manager.activeNotifications.none { it.id == 301 } && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50)
            val notification = manager.activeNotifications.single { it.id == 301 }.notification
            assertNotNull(notification.contentIntent)
            assertEquals("daily_weight", notification.channelId)
            assertNotNull(manager.getNotificationChannel("daily_weight"))
        } finally { manager.cancel(301) }
    }
}
