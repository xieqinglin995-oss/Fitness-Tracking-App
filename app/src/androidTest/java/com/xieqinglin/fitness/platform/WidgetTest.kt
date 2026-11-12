package com.xieqinglin.fitness.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.R
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetTest {
    @Test fun boundWidgetsRespectWeightPrivacyAndSmallVariant() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<FitnessApplication>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = AppWidgetManager.getInstance(app)
        val host = AppWidgetHost(app, 13071)
        val old = app.comfort.state.value
        val large = host.allocateAppWidgetId(); val small = host.allocateAppWidgetId()
        try {
            app.repository.mutate { FitnessData(weights = listOf(WeightEntry(date = java.time.LocalDate.now().toString(), grams = 80250))) }
            app.comfort.update { it.copy(widgetWeight = false) }
            // A test emulator may grant this via `adb shell appwidget grantbind`; normal app installs never request it.
            assumeTrue(manager.bindAppWidgetIdIfAllowed(large, ComponentName(app, FitnessWidget::class.java)))
            assumeTrue(manager.bindAppWidgetIdIfAllowed(small, ComponentName(app, SmallFitnessWidget::class.java)))
            lateinit var largeView: AppWidgetHostView
            lateinit var smallView: AppWidgetHostView
            instrumentation.runOnMainSync {
                host.startListening()
                largeView = host.createView(app, large, manager.getAppWidgetInfo(large))
                smallView = host.createView(app, small, manager.getAppWidgetInfo(small))
            }
            suspend fun awaitValue(expected: String) {
                val end = SystemClock.elapsedRealtime() + 5000
                var text = ""
                while (SystemClock.elapsedRealtime() < end) {
                    instrumentation.runOnMainSync { text = largeView.findViewById<TextView>(R.id.widget_weight)?.text?.toString().orEmpty() }
                    if (text == expected) return
                    SystemClock.sleep(50)
                }
                assertEquals(expected, text)
            }
            FitnessWidget.refresh(app)
            awaitValue("今天已称重")
            instrumentation.runOnMainSync { assertEquals(View.GONE, smallView.findViewById<View>(R.id.widget_training).visibility) }
            app.comfort.update { it.copy(widgetWeight = true) }
            FitnessWidget.refresh(app)
            awaitValue("80.25 kg")
        } finally {
            instrumentation.runOnMainSync { host.stopListening() }
            host.deleteAppWidgetId(large); host.deleteAppWidgetId(small)
            app.comfort.update { old }
        }
    }
}
