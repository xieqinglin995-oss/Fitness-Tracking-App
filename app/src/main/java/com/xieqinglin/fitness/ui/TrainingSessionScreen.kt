package com.xieqinglin.fitness.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

@Composable fun RestTimerCard(vm: FitnessViewModel, compact: Boolean = false) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer) {
        elapsed = SystemClock.elapsedRealtime()
        while (timer.running && timer.remaining(elapsed) > 0) { delay(250); elapsed = SystemClock.elapsedRealtime() }
        if (timer.running && timer.remaining(elapsed) == 0L) vm.finishRest()
    }
    val seconds = (timer.remaining(elapsed) + 999) / 1000
    if (compact) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (timer.running) "组间休息" else "计时已暂停", style = MaterialTheme.typography.labelSmall)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = { vm.adjustRest(-15) }) { Text("−15", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = { if (timer.running) vm.pauseRest() else vm.resumeRest() }) { Icon(if (timer.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (timer.running) "暂停休息计时" else "继续休息计时") }
            IconButton(onClick = { vm.adjustRest(15) }) { Text("+15", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = vm::cancelRest) { Icon(Icons.Outlined.Close, "结束休息") }
        }
        return
    }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("组间休息", style = MaterialTheme.typography.labelMedium)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = { vm.adjustRest(-15) }, enabled = seconds > 0) { Text("−15秒") }
            TextButton(onClick = { if (seconds == 0L) vm.startRest(15) else vm.adjustRest(15) }) { Text("+15秒") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seconds == 0L) {
                listOf(30, 60, 90).forEach { count -> OutlinedButton(onClick = { vm.startRest(count) }) { Text("${count}秒") } }
            } else {
                Button(onClick = { if (timer.running) vm.pauseRest() else vm.resumeRest() }) { Text(if (timer.running) "暂停" else "继续计时") }
                TextButton(onClick = vm::cancelRest) { Text("结束休息") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TrainingSessionScreen(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, snackbar: SnackbarHostState, onExit: () -> Unit) {
    val plan = data.plan(date)
    val options by vm.comfort.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, options.keepAwake) {
        val original = view.keepScreenOn
        view.keepScreenOn = options.keepAwake
        onDispose { view.keepScreenOn = original }
    }
    if (plan == null || plan.exercises.isEmpty()) {
        BackHandler(onBack = onExit)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            EmptyState("这一天还没有训练", "先添加项目，再进入训练模式。")
            Button(onClick = onExit) { Text("返回计划") }
        }
        return
    }
    var selectedId by rememberSaveable(date) { mutableStateOf(TrainingCompletion.eligible(data, date).firstOrNull()?.id ?: plan.exercises.first().id) }
    val item = plan.exercises.find { it.id == selectedId } ?: plan.exercises.first()
    val index = plan.exercises.indexOf(item)
    val record = data.execution(item.id)
    val unit = data.settings.loadUnit
    var text by rememberSaveable(item.id) { mutableStateOf(fitnessJson.encodeToString(ValuesDraft.from(record?.actual ?: item.target, unit))) }
    var savedText by rememberSaveable(item.id) { mutableStateOf(text) }
    var checks by rememberSaveable(item.id) { mutableStateOf(record?.completedSets.orEmpty()) }
    var error by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var skipping by rememberSaveable(item.id) { mutableStateOf(false) }
    val draft = fitnessJson.decodeFromString<ValuesDraft>(text)
    val previous = data.previousExecution(item, date)
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val originalText = savedText
    fun exit() { if (text != originalText) leaving = true else onExit() }
    fun save(completed: List<Int>, finish: Boolean = false, after: () -> Unit = {}) {
        try {
            val actual = draft.parse(item.kind, unit, true)
            if (finish && item.kind != ExerciseKind.STRENGTH) Rules.values(item.kind, actual)
            vm.saveSession(date, item, actual, completed, finish) { checks = completed; savedText = text; after() }
            error = null
        } catch (e: IllegalArgumentException) { error = e.message }
    }
    BackHandler(onBack = ::exit)
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (timer.running || timer.pausedMillis > 0) Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
        ) { Box(Modifier.navigationBarsPadding()) { RestTimerCard(vm, compact = true) } }
    }, topBar = {
        TopAppBar(title = { Column { Text("专注训练", style = MaterialTheme.typography.titleMedium); Text(date, style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = ::exit, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "退出训练") } },
            actions = { Text("${data.plan(date)!!.exercises.count { data.execution(it.id)?.status == ExecutionStatus.DONE }} / ${plan.exercises.size}", Modifier.padding(end = 16.dp)) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.exercises.forEachIndexed { position, exercise ->
                        FilterChip(item.id == exercise.id, {
                            if (text != originalText) error = "先保存当前数字，再切换动作" else selectedId = exercise.id
                        }, label = { Text("${position + 1}. ${exercise.name}") })
                    }
                }
            }
            item { SectionCard {
                Text(item.name, style = MaterialTheme.typography.headlineMedium)
                Text("计划 ${item.target.summary(item.kind, unit)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (previous != null) Text("上次 ${previous.date.substring(5)}  ${previous.actual.summary(previous.kindSnapshot, unit)}", style = MaterialTheme.typography.bodySmall)
                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                Text(if (item.kind == ExerciseKind.STRENGTH) "做完一组，勾选一次。未勾选的数值仍待确认。" else "核对实际时长或距离，再确认完成。", style = MaterialTheme.typography.bodySmall)
                ValuesFields(item.kind, unit, draft, completed = if (item.kind == ExerciseKind.STRENGTH) checks else null,
                    onToggle = { set ->
                        val next = if (set in checks) checks - set else (checks + set).sorted()
                        save(next) {
                            if (set in next) {
                                keyboard?.hide()
                                if (options.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (next.size < draft.reps.size && options.restSeconds > 0) vm.startRest()
                            }
                        }
                    }, enabled = !busy) { text = fitnessJson.encodeToString(it); error = null }
                ErrorText(error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { save(checks) }, enabled = !busy) { Text("保存草稿") }
                    Button(onClick = { save(if (item.kind == ExerciseKind.STRENGTH) draft.reps.indices.toList() else emptyList(), true) }, enabled = !busy) { Text("完成动作") }
                }
                TextButton(onClick = { skipping = true }, enabled = !busy) { Text("跳过这个动作") }
            } }
            if (!timer.running && timer.pausedMillis == 0L) item { RestTimerCard(vm) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (text != originalText) error = "请先保存当前数字" else selectedId = plan.exercises[(index - 1).coerceAtLeast(0)].id }, enabled = !busy && index > 0, modifier = Modifier.weight(1f)) { Text("上一个动作") }
                    Button(onClick = {
                        if (text != originalText) error = "请先保存当前数字"
                        else if (index < plan.exercises.lastIndex) selectedId = plan.exercises[index + 1].id else onExit()
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (index < plan.exercises.lastIndex) "下一个动作" else "回到训练清单") }
                }
            }
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("保存当前输入？") },
        text = { Text("已确认的组已经保存。本次修改的数字可存为草稿，下次继续。") },
        confirmButton = { TextButton(onClick = { save(checks, after = onExit) }, enabled = !busy) { Text("保存并返回") } },
        dismissButton = { Row { TextButton(onClick = { leaving = false }) { Text("继续训练") }; TextButton(onClick = onExit, enabled = !busy) { Text("放弃本次输入") } } })
    if (skipping) ConfirmDialog("跳过 ${item.name}？", "会保留填写的实际数值，但清除该动作的组完成标记。", "跳过", busy, { skipping = false }) {
        try { val actual = draft.parse(item.kind, unit, true)
            vm.action("已跳过", { skipping = false }) { vm.repository.saveExecution(date, item, actual, ExecutionStatus.SKIPPED, "") }
        } catch (e: IllegalArgumentException) { error = e.message; skipping = false }
    }
}
