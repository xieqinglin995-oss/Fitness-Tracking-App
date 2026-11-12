package com.xieqinglin.fitness.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrendsScreen(data: FitnessData, today: LocalDate, onEdit: (WeightEntry?) -> Unit, onTraining: (String) -> Unit, vm: FitnessViewModel) {
    var history by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableIntStateOf(30) }
    var showGoal by rememberSaveable { mutableStateOf(false) }
    var showAverage by rememberSaveable { mutableStateOf(true) }
    var explain by rememberSaveable { mutableStateOf(false) }
    val from = if (range == 0) null else today.minusDays(range.toLong() - 1)
    val stats = remember(data.weights, from, today) { Statistics.stats(data.weights, from, today) }
    val unit = data.settings.unit
    val rangeLabel = if (range == 0) "全部历史" else "近 $range 天"
    val trainingDates = remember(data, from, today) { data.executions.filter { it.hasConfirmedActivity() && (from == null || it.date >= from.toString()) && it.date <= today.toString() }.map { it.date }.distinct().sortedDescending() }
    val overview = remember(data, today) { data.weekOverview(today) }
    val comparison = remember(data.weights, today) { Statistics.comparison(data.weights, today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90, 0).forEach { days -> FilterChip(range == days, { range = days }, label = { Text(if (days == 0) "全部" else "近 $days 天") }) }
            }
        }
        item { SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("最近体重", stats.latest?.let { "${formatWeight(it.grams, unit)} ${unit.label}" } ?: "暂无记录", Modifier.weight(1f), stats.latest?.date)
                Metric("阶段变化", stats.change?.let { "${formatDelta(it, unit)} ${unit.label}" } ?: "数据不足", Modifier.weight(1f), "${stats.entries.size} 天记录 · $rangeLabel")
            }
        } }
        item { SectionCard(spacing = 4.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("体重走势", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { explain = !explain }) { Text(if (explain) "收起说明" else "图表说明") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(showAverage, { showAverage = !showAverage }, label = { Text("7 日均值") })
                if (data.settings.goalGrams != null) FilterChip(showGoal, { showGoal = !showGoal }, label = { Text("目标线") })
            }
            WeightChart(data.weights, from, today, unit, if (showGoal) data.settings.goalGrams else null, showAverage = showAverage)
            if (explain) Text("均值使用当日及前 6 天的实际记录，至少 3 天记录才显示。缺失日期不补值。目标线默认隐藏，避免压缩日常波动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { OutlinedButton(onClick = { history = true }, modifier = Modifier.fillMaxWidth()) { Text("力量与有氧回看") } }
        if (trainingDates.isNotEmpty()) item { SectionCard {
            Text("● 实际训练日期", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trainingDates, key = { it }) { date -> AssistChip(onClick = { onTraining(date) }, label = { Text("● $date") }) }
            }
        } }
        item { SectionCard {
            Text("更多数据", style = MaterialTheme.typography.titleMedium)
            Text("最近 7 日与此前 7 日均值", style = MaterialTheme.typography.titleSmall)
            Text("${today.minusDays(6)} 至 $today：${comparison.recent.mean?.let { formatWeight(it.roundToLong(), unit) } ?: "—"} ${unit.label}（${comparison.recent.entries.size} 天）\n" +
                "${today.minusDays(13)} 至 ${today.minusDays(7)}：${comparison.previous.mean?.let { formatWeight(it.roundToLong(), unit) } ?: "—"} ${unit.label}（${comparison.previous.entries.size} 天）", style = MaterialTheme.typography.bodySmall)
            Text(comparison.delta?.let { "均值差 ${formatDelta(it, unit)} ${unit.label}" } ?: "两段各至少 3 天记录后显示差值", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("本周训练", "${overview.trainedDays} 天", Modifier.weight(1f), "按已有确认记录的日期统计")
                Metric("本周称重", "${overview.recordedDays} 天", Modifier.weight(1f), "截至今天")
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric("较上次变化", stats.sincePrevious?.let { "${formatDelta(it, unit)} ${unit.label}" } ?: "数据不足", Modifier.weight(1f), stats.previous?.let { "${it.date}\n至 ${stats.latest!!.date}" })
                Metric("区间平均", stats.mean?.let { "${formatWeight(it.roundToLong(), unit)} ${unit.label}" } ?: "暂无记录", Modifier.weight(1f), "记录 ${stats.entries.size} 天")
            }
            if (data.settings.goalGrams != null && stats.latest != null) {
                HorizontalDivider()
                Metric("最近体重 − 目标体重", "${formatDelta(stats.latest.grams.toDouble() - data.settings.goalGrams, unit)} ${unit.label}")
            }
        } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("体重历史", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(null) }) { Text("补录体重") }
            }
            if (stats.entries.isEmpty()) EmptyState("这段时间还没有记录", "可以补录过去的体重，或换个时间范围看看。")
        }
        stats.entries.asReversed().forEach { entry -> item(key = entry.id) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatWeight(entry.grams, unit)} ${unit.label}", style = MaterialTheme.typography.titleLarge)
                        Text(entry.date, style = MaterialTheme.typography.bodyMedium)
                        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onEdit(entry) }) { Icon(Icons.Outlined.Edit, "编辑 ${entry.date} 的体重") }
                }
                TextButton(onClick = { onTraining(entry.date) }) { Text("查看当天训练") }
            }
        } }
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    if (history) TrainingHistory(data, vm, busy, { history = false }, onTraining)
}
