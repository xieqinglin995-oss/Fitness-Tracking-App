package com.xieqinglin.fitness.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FitnessApp(vm: FitnessViewModel) {
    val screen by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val data = screen.data
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var weightOpen by rememberSaveable { mutableStateOf(false) }
    var weightId by rememberSaveable { mutableStateOf<String?>(null) }
    var weightDate by rememberSaveable { mutableStateOf(today.toString()) }
    var executionDate by rememberSaveable { mutableStateOf<String?>(null) }
    var executionId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); delay(15_000) } }
    BackHandler(tab != 0 && !busy) { tab = 0 }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::inspect) }
    val safety = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::restoreWithSafetyCopy) }
    fun openWeight(entry: WeightEntry?, date: String = today.toString()) { weightId = entry?.id; weightDate = date; weightOpen = true }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(listOf("日日健身", "训练计划", "体重趋势", "设置与数据")[tab], fontWeight = FontWeight.Bold) },
                actions = { if (tab != 3) IconButton(onClick = { tab = 3 }) { Icon(Icons.Outlined.Settings, "设置") } else IconButton(onClick = { tab = 0 }) { Icon(Icons.Outlined.Close, "返回今天") } })
        },
        bottomBar = { if (tab != 3) NavigationBar {
            listOf("今天" to Icons.Outlined.Home, "计划" to Icons.Outlined.CalendarMonth, "趋势" to Icons.AutoMirrored.Outlined.ShowChart).forEachIndexed { index, (label, icon) ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                screen.error != null -> Column(Modifier.padding(24.dp)) { ErrorText(screen.error); Button(onClick = vm::reload) { Text("重试读取") } }
                !screen.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tab == 0 -> TodayScreen(data, today, { openWeight(data.weights.find { it.date == today.toString() }) }, { tab = 2 }, { selectedDate = today.toString(); tab = 1 }, vm, busy,
                    onExecute = { date, id -> executionDate = date; executionId = id })
                tab == 1 -> PlanScreen(data, selectedDate, { selectedDate = it }, today, vm, busy)
                tab == 2 -> TrendsScreen(data, today, { openWeight(it, it?.date ?: today.toString()) }, { selectedDate = it; tab = 1 })
                tab == 3 -> SettingsScreen(data, vm, busy, { export.launch("fitness-$today.json") }, { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (weightOpen) WeightForm(data, data.weights.find { it.id == weightId }, weightDate, vm, busy) { weightOpen = false }
    val executionItem = data.plan(executionDate.orEmpty())?.exercises?.find { it.id == executionId }
    if (executionItem != null) ExecutionForm(executionDate!!, executionItem, data.execution(executionItem.id), vm, data.settings.unit, busy) { executionId = null; executionDate = null }
    preview?.let { backup ->
        val c = backup.counts
        ConfirmDialog("完整替换本机数据？", "备份时间：${backup.exportedAt}\n将导入：${c.weights} 条体重、${c.plans} 日计划、${c.executions} 条实际、${c.templates} 个模板及设置。\n\n本机现有 ${data.weights.size} 条体重、${data.plans.size} 日计划、${data.executions.size} 条实际、${data.templates.size} 个模板及设置将被完整替换。\n\n继续后请先选择原数据备份的保存位置；备份成功后才会恢复。取消文件选择不会修改数据。", "备份原数据并恢复", busy,
            vm::cancelImport, { safety.launch("fitness-before-restore-$today.json") })
    }
}

@Composable private fun TodayScreen(data: FitnessData, today: LocalDate, onWeight: () -> Unit, onTrend: () -> Unit, onPlan: () -> Unit, vm: FitnessViewModel, busy: Boolean, onExecute: (String, String) -> Unit) {
    val date = today.toString()
    val weight = data.weights.find { it.date == date }
    val latest = data.weights.maxByOrNull { it.date }
    val unit = data.settings.unit
    val plan = data.plan(date)
    val done = plan?.exercises.orEmpty().count { data.execution(it.id)?.status == ExecutionStatus.DONE }
    var temporaryOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(displayDate(today), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("照顾自己，从今天开始", style = MaterialTheme.typography.headlineMedium)
            Text("记录一点变化，完成自己的节奏。", style = MaterialTheme.typography.bodyMedium)
        } }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("今日体重", style = MaterialTheme.typography.titleMedium)
                    if (weight != null) Text("${formatWeight(weight.grams, unit)} ${unit.label}", fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    else {
                        Text("今天还没记录", style = MaterialTheme.typography.headlineMedium)
                        Text(latest?.let { "最近一次：${it.date} · ${formatWeight(it.grams, unit)} ${unit.label}" } ?: "第一次记录，不需要任何准备。")
                    }
                    Button(onClick = onWeight, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(if (weight == null) "记录今日体重" else "编辑今日体重") }
                }
            }
        }
        item { SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("今天的训练", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onPlan) { Text("安排训练") }
            }
            Text("${data.dayStatus(date).label}" + if (plan?.exercises?.isNotEmpty() == true) " · 完成 $done / ${plan.exercises.size} 项 · 跳过 ${plan.exercises.count { data.execution(it.id)?.status == ExecutionStatus.SKIPPED }} 项" else "", color = MaterialTheme.colorScheme.primary)
            if (plan?.exercises?.isNotEmpty() == true) {
                LinearProgressIndicator(progress = { done.toFloat() / plan.exercises.size }, modifier = Modifier.fillMaxWidth())
                Text("归属日期：$date", style = MaterialTheme.typography.labelMedium)
            } else if (plan?.rest != true) {
                Text("今天还没有训练安排。去计划页添加项目，或设为休息日。")
                OutlinedButton(onClick = { vm.change { PlanOperations.rest(it, date, true) } }, enabled = !busy) { Text("设为休息日") }
            } else Text("给恢复留一点时间，也是一种安排。")
            TextButton(onClick = { temporaryOpen = true }, enabled = !busy) { Text("＋ 临时新增训练") }
        } }
        plan?.exercises.orEmpty().forEach { exercise -> item(key = exercise.id) { WorkoutCard(exercise, date, data, vm, busy, onExecute = { onExecute(date, exercise.id) }) } }
        item { SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("近 7 天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onTrend) { Text("查看趋势") }
            }
            WeightChart(data.weights, today.minusDays(6), today, unit, data.settings.goalGrams, compact = true, onOpen = onTrend)
        } }
    }
    if (temporaryOpen) ExerciseForm(null, unit, busy, temporary = true, onDismiss = { temporaryOpen = false }) { exercise ->
        vm.action(onSuccess = { temporaryOpen = false }) { vm.repository.saveExercise(date, exercise) }
    }
}
