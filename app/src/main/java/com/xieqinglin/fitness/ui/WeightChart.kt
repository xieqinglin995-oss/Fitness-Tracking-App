package com.xieqinglin.fitness.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

@Composable fun WeightChart(entries: List<WeightEntry>, from: LocalDate?, today: LocalDate, unit: WeightUnit, goal: Long?, compact: Boolean = false, onOpen: (() -> Unit)? = null) {
    val points = remember(entries, from, today) { Statistics.points(entries, from, today) }
    if (points.isEmpty()) { EmptyState("从一次记录开始", "记录体重后，这里会显示真实测量与 7 日移动平均。"); return }
    val start = from ?: points.first().date
    val end = today
    val days = max(1, end.toEpochDay() - start.toEpochDay())
    val allValues = points.flatMap { listOfNotNull(it.grams.toDouble(), it.averageGrams) } + listOfNotNull(goal?.toDouble())
    val min = allValues.min()
    val max = allValues.max()
    val span = max(2000.0, max - min)
    val low = (min + max) / 2 - span * .65
    val high = (min + max) / 2 + span * .65
    val rawColor = MaterialTheme.colorScheme.primary
    val averageColor = MaterialTheme.colorScheme.tertiary
    val goalColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = points.find { it.date.toString() == selectedDate }
    Text("体重 · ${unit.label}", style = MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(if (compact) 160.dp else 240.dp)
        .semantics { contentDescription = "体重趋势图，${points.size} 个测量点，单位 ${unit.label}。实线为测量，虚线为移动平均。详细值见下方历史记录。" }
        .pointerInput(points, start, end) {
            detectTapGestures { position ->
                if (onOpen != null) { onOpen(); return@detectTapGestures }
                val left = 56.dp.toPx(); val width = size.width - left - 16.dp.toPx()
                selectedDate = points.minByOrNull { abs(left + (it.date.toEpochDay() - start.toEpochDay()).toFloat() / days * width - position.x) }?.date?.toString()
            }
        }) {
        val left = 56.dp.toPx(); val right = size.width - 16.dp.toPx()
        val top = 12.dp.toPx(); val bottom = size.height - 26.dp.toPx()
        fun x(date: LocalDate) = left + (date.toEpochDay() - start.toEpochDay()).toFloat() / days * (right - left)
        fun y(grams: Double) = bottom - ((grams - low) / (high - low)).toFloat() * (bottom - top)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelColor.toArgb(); textSize = 10.dp.toPx() }
        for (i in 0..4) {
            val value = low + (high - low) * i / 4
            drawLine(gridColor, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
            drawIntoCanvas { it.nativeCanvas.drawText(formatWeight(value.roundToLong(), unit), 0f, y(value) + 4.dp.toPx(), paint) }
        }
        val formatter = DateTimeFormatter.ofPattern("M/d")
        drawIntoCanvas {
            it.nativeCanvas.drawText(start.format(formatter), left, size.height - 2.dp.toPx(), paint)
            paint.textAlign = Paint.Align.RIGHT
            it.nativeCanvas.drawText(end.format(formatter), right, size.height - 2.dp.toPx(), paint)
        }
        goal?.let { value ->
            drawPath(Path().apply { moveTo(left, y(value.toDouble())); lineTo(right, y(value.toDouble())) }, goalColor,
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))))
        }
        points.zipWithNext().forEach { (a, b) ->
            if (a.date.plusDays(1) == b.date) {
                drawLine(rawColor, Offset(x(a.date), y(a.grams.toDouble())), Offset(x(b.date), y(b.grams.toDouble())), 1.5.dp.toPx())
                if (a.averageGrams != null && b.averageGrams != null) {
                    drawPath(Path().apply { moveTo(x(a.date), y(a.averageGrams)); lineTo(x(b.date), y(b.averageGrams)) }, averageColor,
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))))
                }
            }
        }
        points.forEach { point ->
            drawCircle(rawColor, if (point == selected) 6.dp.toPx() else 3.dp.toPx(), Offset(x(point.date), y(point.grams.toDouble())))
            point.averageGrams?.let { drawRect(averageColor, Offset(x(point.date) - 2.dp.toPx(), y(it) - 2.dp.toPx()), androidx.compose.ui.geometry.Size(4.dp.toPx(), 4.dp.toPx())) }
        }
    }
    if (!compact) {
        Text("● 测量（细实线）   ▪ 7 日均值（长虚线）" + if (goal != null) "\n··· 目标 ${formatWeight(goal, unit)} ${unit.label}（短虚线）" else "", style = MaterialTheme.typography.bodySmall)
        if (selected != null) Text("${selected.date} · ${formatWeight(selected.grams, unit)} ${unit.label}" +
            (selected.averageGrams?.let { "\n7 日均值 ${formatWeight(it.roundToLong(), unit)} ${unit.label} · ${selected.samples} 天样本" } ?: "\n7 日窗口内不足 3 天记录，暂无均值"), style = MaterialTheme.typography.bodyMedium)
        Text("点击图表查看测量点。7 日移动平均根据窗口内实际记录计算，缺失日期不补值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
