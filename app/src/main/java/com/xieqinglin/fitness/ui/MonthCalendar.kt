package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable fun MonthCalendar(data: FitnessData, selected: LocalDate, onDismiss: () -> Unit, onDate: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month = YearMonth.parse(monthText)
    val first = month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val weights = data.weights.map { it.date }.toSet()
    FormDialog("训练月历", false, onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
            Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall) } }
        (0 until cells / 7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..6).forEach { day ->
                    val number = week * 7 + day - offset + 1
                    if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                        val date = month.atDay(number)
                        val status = data.dayStatus(date.toString())
                        val label = when (status) { DayStatus.DONE -> "✓"; DayStatus.REST -> "休"; DayStatus.PENDING, DayStatus.ACTIVE, DayStatus.ENDED -> "练"; else -> "" }
                        Surface(onClick = { onDate(date.toString()); onDismiss() }, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "$date ${status.label}" + if (date.toString() in weights) " 已称重" else "" },
                            shape = MaterialTheme.shapes.small,
                            border = if (date == LocalDate.now()) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(number.toString(), style = MaterialTheme.typography.bodyMedium)
                                Text(label + if (date.toString() in weights) "•" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        Text("✓ 训练完成  练 有训练安排  休 休息日  • 已称重", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { monthText = YearMonth.now().toString() }) { Text("回到本月") }
    }
}
