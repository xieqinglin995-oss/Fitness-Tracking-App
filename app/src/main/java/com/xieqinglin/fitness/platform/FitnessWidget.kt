package com.xieqinglin.fitness.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.MainActivity
import com.xieqinglin.fitness.R
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.*
import java.time.LocalDate

open class FitnessWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshAsync(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refreshAsync(context)
    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8000) { refresh(context) } }
            catch (_: Exception) { android.util.Log.w("FitnessWidget", "Widget refresh failed; open app to retry") }
            finally { pending.finish() }
        }
    }
    companion object {
        fun installed(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return listOf(FitnessWidget::class.java, SmallFitnessWidget::class.java).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        }
        suspend fun refresh(context: Context, data: FitnessData? = null) {
            if (!installed(context)) return
            val app = context.applicationContext as FitnessApplication
            val snapshot = data ?: app.repository.snapshot()
            val manager = AppWidgetManager.getInstance(context)
            val date = LocalDate.now().toString()
            val weight = snapshot.weights.find { it.date == date }
            val options = app.comfort.state.value
            val dark = options.theme == ThemeChoice.DARK || (options.theme == ThemeChoice.SYSTEM && context.resources.configuration.uiMode and 0x30 == 0x20)
            listOf(FitnessWidget::class.java, SmallFitnessWidget::class.java).forEach { component ->
                manager.getAppWidgetIds(ComponentName(context, component)).forEach { id ->
                    val view = RemoteViews(context.packageName, R.layout.fitness_widget)
                    view.setTextViewText(R.id.widget_date, "好好健身 · $date")
                    view.setTextViewText(R.id.widget_weight, if (weight == null) "今天尚未称重" else if (options.widgetWeight) "${formatWeight(weight.grams, snapshot.settings.unit)} ${snapshot.settings.unit.label}" else "今天已称重")
                    view.setTextViewText(R.id.widget_training, "训练 · ${snapshot.dayStatus(date).label}")
                    view.setViewVisibility(R.id.widget_training, if (component == SmallFitnessWidget::class.java) View.GONE else View.VISIBLE)
                    view.setInt(R.id.widget_root, "setBackgroundColor", if (dark) 0xFF1B2334.toInt() else 0xFFF3F5F9.toInt())
                    listOf(R.id.widget_date, R.id.widget_weight, R.id.widget_training).forEach { text -> view.setTextColor(text, if (dark) 0xFFE4E9F4.toInt() else 0xFF18243E.toInt()) }
                    listOf(R.id.widget_weight to "weight", R.id.widget_training to "training").forEach { (target, action) ->
                        val intent = Intent(context, MainActivity::class.java).putExtra("open_action", action)
                            .setData(Uri.parse("haoh://widget/$id/$action")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        view.setOnClickPendingIntent(target, PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    }
                    manager.updateAppWidget(id, view)
                }
            }
        }
    }
}
class SmallFitnessWidget : FitnessWidget()
