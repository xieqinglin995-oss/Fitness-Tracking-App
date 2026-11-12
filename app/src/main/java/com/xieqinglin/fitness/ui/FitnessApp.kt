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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.compose.LocalActivity
import com.xieqinglin.fitness.MainActivity
import com.xieqinglin.fitness.backup.fitnessJson
import kotlinx.serialization.encodeToString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.xieqinglin.fitness.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FitnessApp(vm: FitnessViewModel) {
    val screen by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val data = screen.data
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var previousTab by rememberSaveable { mutableIntStateOf(0) }
    var sessionDate by rememberSaveable { mutableStateOf<String?>(null) }
    var csvTraining by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveableStateHolder()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var weightOpen by rememberSaveable { mutableStateOf(false) }
    var weightId by rememberSaveable { mutableStateOf<String?>(null) }
    var weightDate by rememberSaveable { mutableStateOf(today.toString()) }
    var executionDate by rememberSaveable { mutableStateOf<String?>(null) }
    var executionId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { notice ->
        val result = snackbar.showSnackbar(notice.text, actionLabel = if (notice.undo != null) "撤销" else null,
            withDismissAction = notice.undo != null, duration = if (notice.undo != null) SnackbarDuration.Long else SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) notice.undo?.let(vm::undoCompletion)
    } }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { today = LocalDate.now(); com.xieqinglin.fitness.platform.Reminders.schedule(context) } }
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { today = LocalDate.now() } }
        owner.lifecycle.addObserver(observer)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { owner.lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    BackHandler(tab != 0 && !busy) { tab = if (tab == 3) previousTab else 0 }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::inspect) }
    val safety = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::restoreWithSafetyCopy) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCsv(it, csvTraining) } }
    fun openWeight(entry: WeightEntry?, date: String = today.toString()) { weightId = entry?.id; weightDate = date; weightOpen = true }
    val activity = LocalActivity.current as MainActivity
    val requested by activity.requestedAction.collectAsStateWithLifecycle()
    LaunchedEffect(requested, screen.loaded, sessionDate) {
        if (screen.loaded && requested != null && sessionDate == null) {
            if (requested == "weight") { tab = 0; openWeight(data.weights.find { it.date == today.toString() }) }
            else { tab = 0; if (data.plan(today.toString())?.exercises?.isNotEmpty() == true) sessionDate = today.toString() }
            activity.requestedAction.value = null
        }
    }
    if (sessionDate != null && screen.loaded) {
        TrainingSessionScreen(data, sessionDate!!, vm, busy, snackbar) { sessionDate = null }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(listOf(stringResource(R.string.app_name), "训练计划", "体重趋势", "设置与数据")[tab], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = { if (tab != 3) IconButton(onClick = { previousTab = tab; tab = 3 }) { Icon(Icons.Outlined.Settings, "设置") } else IconButton(onClick = { tab = previousTab }) { Icon(Icons.Outlined.Close, "返回上一页") } })
        },
        bottomBar = { if (tab != 3) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            listOf("今天" to Icons.Outlined.Home, "计划" to Icons.Outlined.CalendarMonth, "趋势" to Icons.AutoMirrored.Outlined.ShowChart).forEachIndexed { index, (label, icon) ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, label) }, label = { Text(label) })
            }
        } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            pages.SaveableStateProvider(tab) { when {
                screen.error != null -> Column(Modifier.padding(24.dp)) { ErrorText(screen.error); Button(onClick = vm::reload) { Text("重试读取") } }
                !screen.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                tab == 0 -> TodayScreen(data, today, { openWeight(data.weights.find { it.date == today.toString() }) }, { tab = 2 }, { selectedDate = today.toString(); tab = 1 }, vm, busy,
                    onExecute = { date, id -> executionDate = date; executionId = id }, onStartSession = { sessionDate = it })
                tab == 1 -> PlanScreen(data, selectedDate, { selectedDate = it }, today, vm, busy, onStartSession = { sessionDate = it })
                tab == 2 -> TrendsScreen(data, today, { openWeight(it, it?.date ?: today.toString()) }, { selectedDate = it; tab = 1 }, vm)
                tab == 3 -> SettingsScreen(data, vm, busy, { export.launch("fitness-$today.json") }, { import.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                    onCsv = { training -> csvTraining = training; csv.launch(if (training) "training-$today.csv" else "weight-$today.csv") })
            } }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (weightOpen) WeightForm(data, data.weights.find { it.id == weightId }, weightDate, vm, busy) { weightOpen = false }
    val executionItem = data.plan(executionDate.orEmpty())?.exercises?.find { it.id == executionId }
    if (executionItem != null) ExecutionForm(executionDate!!, executionItem, data.execution(executionItem.id), vm, data.settings.loadUnit, busy) { executionId = null; executionDate = null }
    preview?.let { backup ->
        val c = backup.counts
        val future = backup.data.weights.count { it.date > today.toString() } + backup.data.executions.count { it.date > today.toString() }
        val warning = "\n另含 ${c.definitions} 个动作定义、${c.sessions} 次总结、${c.sources} 个排期来源。\n" + if (future > 0) "\n有 $future 条记录归属日期晚于设备今天，请核对系统日期；恢复会保留原日期。\n" else ""
        ConfirmDialog("完整替换本机数据？", "备份时间：${backup.exportedAt}\n将导入：${c.weights} 条体重、${c.plans} 日计划、${c.executions} 条实际、${c.templates} 个模板及设置。$warning\n\n本机现有 ${data.weights.size} 条体重、${data.plans.size} 日计划、${data.executions.size} 条实际、${data.templates.size} 个模板及设置将被完整替换。\n\n继续后请先选择原数据备份的保存位置；备份成功后才会恢复。取消文件选择不会修改数据。", "备份原数据并恢复", busy,
            vm::cancelImport, { safety.launch("fitness-before-restore-$today.json") })
    }
}

@Composable private fun TodayScreen(data: FitnessData, today: LocalDate, onWeight: () -> Unit, onTrend: () -> Unit, onPlan: () -> Unit, vm: FitnessViewModel, busy: Boolean, onExecute: (String, String) -> Unit, onStartSession: (String) -> Unit) {
    val date = today.toString()
    val weight = data.weights.find { it.date == date }
    val latest = data.weights.maxByOrNull { it.date }
    val unit = data.settings.unit
    val plan = data.plan(date)
    val done = plan?.exercises.orEmpty().count { data.execution(it.id)?.status == ExecutionStatus.DONE }
    val pending = TrainingCompletion.eligible(data, date).size
    val prior = data.weights.filter { it.date < date }.maxByOrNull { it.date }
    var temporaryOpen by rememberSaveable { mutableStateOf(false) }
    var batchOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var seedText by rememberSaveable { mutableStateOf<String?>(null) }
    var repeatOpen by rememberSaveable { mutableStateOf(false) }
    var weightExpanded by rememberSaveable(date) { mutableStateOf(false) }
    var summaryOpen by rememberSaveable(date) { mutableStateOf(false) }
    val recentPlan = data.plans.filter { it.date < date && it.exercises.isNotEmpty() }.maxByOrNull { it.date }
    val overview = remember(data, today) { data.weekOverview(today) }
    val timer by vm.timer.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(displayDate(today), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onTrend) { Text("本周训练 ${overview.trainedDays} 天") }
        } }
        item {
            if (weight != null && !weightExpanded) RecordedWeight(weight, unit, busy, onWeight) { weightExpanded = true }
            else {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF18243E), contentColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("今日体重", style = MaterialTheme.typography.titleMedium)
                        Text(if (weight == null) "等待记录" else "已记录", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA5E4D4))
                    }
                    if (weight != null) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(formatWeight(weight.grams, unit), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Text(unit.label, modifier = Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(if (prior != null) "较 ${prior.date.substring(5)}  ${formatDelta(weight.grams.toDouble() - prior.grams, unit)} ${unit.label}" else "第一条体重，记录从这里开始", color = Color(0xFFC3CDE0), style = MaterialTheme.typography.bodySmall)
                    }
                    else {
                        Text("今天还没记录", style = MaterialTheme.typography.headlineMedium)
                        Text(latest?.let { "最近 ${it.date} · ${formatWeight(it.grams, unit)} ${unit.label}" } ?: "支持斤和公斤，按自己的习惯来。", color = Color(0xFFC3CDE0), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onWeight, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB6EADB), contentColor = Color(0xFF142E2B))) {
                        Icon(if (weight == null) Icons.Outlined.Add else Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(if (weight == null) "记录今日体重" else "编辑今日体重")
                    }
                    if (weight != null) TextButton(onClick = { weightExpanded = false }) { Text("收起体重详情", color = Color.White) }
                }
            }
            }
        }
        item { SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日训练", style = MaterialTheme.typography.titleLarge)
                    Text(if (plan?.exercises?.isNotEmpty() == true) "完成 $done / ${plan.exercises.size} 项 · ${data.dayStatus(date).label}" else data.dayStatus(date).label,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onPlan) { Text("管理计划") }
            }
            if (plan?.exercises?.isNotEmpty() == true) {
                val next = plan.exercises.firstOrNull { data.execution(it.id)?.status !in listOf(ExecutionStatus.DONE, ExecutionStatus.SKIPPED) }
                if (next != null && data.execution(next.id) != null) Text("接着做 ${next.name} · 已确认 ${data.execution(next.id)?.completedSets?.size ?: 0} 组", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { done.toFloat() / plan.exercises.size }, modifier = Modifier.fillMaxWidth().height(6.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val start: @Composable (Modifier) -> Unit = { modifier ->
                        Button(onClick = { onStartSession(date) }, enabled = !busy, modifier = modifier) { Text(if (pending == 0) "回看训练" else if (done > 0 || data.dayStatus(date) == DayStatus.ACTIVE) "继续训练" else "开始训练") }
                    }
                    if (maxWidth < 270.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            start(Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { batchOpen = true }, enabled = !busy && pending > 0, modifier = Modifier.fillMaxWidth()) { Text("批量完成") }
                            TextButton(onClick = { pickerOpen = true }, enabled = !busy) { Icon(Icons.Outlined.Add, null); Text("临时新增训练") }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        start(Modifier.weight(1f))
                        OutlinedButton(onClick = { batchOpen = true }, enabled = !busy && pending > 0) { Text("批量完成") }
                        IconButton(onClick = { pickerOpen = true }, enabled = !busy) { Icon(Icons.Outlined.Add, "临时新增训练") }
                    }
                }
                TextButton(onClick = { summaryOpen = true }, enabled = !busy) { Text("本次总结 / 保留部分结束") }
            } else if (plan?.rest != true) {
                Text("添加今天的项目，或从计划页应用模板。", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickerOpen = true }, enabled = !busy) { Text("添加训练") }
                    TextButton(onClick = { vm.change { PlanOperations.rest(it, date, true) } }, enabled = !busy) { Text("设为休息日") }
                }
                if (recentPlan != null) TextButton(onClick = { repeatOpen = true }, enabled = !busy) { Text("复用最近训练 · ${recentPlan.date.substring(5)}") }
            } else {
                Text("今天安排休息，恢复也是训练的一部分。", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { pickerOpen = true }, enabled = !busy) { Text("临时新增训练") }
            }
        } }
        if (timer.running || timer.pausedMillis > 0) item { RestTimerCard(vm) }
        plan?.exercises.orEmpty().forEach { exercise -> item(key = exercise.id) { WorkoutCard(exercise, date, data, vm, busy, onExecute = { onExecute(date, exercise.id) }) } }
        item { SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("近期体重", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onTrend) { Text("查看趋势") }
            }
            WeightChart(data.weights, today.minusDays(6), today, unit, null, compact = true, onOpen = onTrend)
        } }
    }
    if (batchOpen) BatchCompletionDialog(data, date, vm, busy, onDismiss = { batchOpen = false })
    if (summaryOpen) SessionSummary(data, date, vm, busy, { summaryOpen = false }, { summaryOpen = false })
    if (repeatOpen && recentPlan != null) ConfirmDialog("复用最近一次训练？", "将 ${recentPlan.date} 的 ${recentPlan.exercises.size} 个项目安排到今天，只复制目标，不复制完成状态。", "使用这个计划", busy, { repeatOpen = false }) {
        vm.change("训练已安排", { repeatOpen = false }) { PlanOperations.apply(it, date, recentPlan.exercises, recentPlan.name, ConflictChoice.APPEND) }
    }
    if (pickerOpen) ExercisePicker(data, busy, { pickerOpen = false }) { seed -> seedText = seed?.let { fitnessJson.encodeToString(it) }; pickerOpen = false; temporaryOpen = true }
    if (temporaryOpen) ExerciseForm(null, data.settings.loadUnit, busy, temporary = true, onDismiss = { temporaryOpen = false }, seed = seedText?.let { fitnessJson.decodeFromString<Exercise>(it) }) { exercise ->
        vm.action(onSuccess = { temporaryOpen = false }) { vm.repository.saveExercise(date, exercise) }
    }
}

@Composable private fun RecordedWeight(weight: WeightEntry, unit: WeightUnit, busy: Boolean, onEdit: () -> Unit, onExpand: () -> Unit) {
    SectionCard {
        Text("今日体重 · 已记录", style = MaterialTheme.typography.labelLarge)
        BoxWithConstraints {
            val numbers: @Composable () -> Unit = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatWeight(weight.grams, unit), style = MaterialTheme.typography.titleLarge); Text(unit.label, style = MaterialTheme.typography.titleLarge)
            } }
            if (maxWidth < 300.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) Column {
                numbers()
                Row { TextButton(onClick = onEdit, enabled = !busy) { Text("编辑今日体重") }; IconButton(onClick = onExpand) { Icon(Icons.Outlined.ExpandMore, "展开体重详情") } }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { numbers() }
                TextButton(onClick = onEdit, enabled = !busy) { Text("编辑今日体重") }
                IconButton(onClick = onExpand) { Icon(Icons.Outlined.ExpandMore, "展开体重详情") }
            }
        }
    }
}
