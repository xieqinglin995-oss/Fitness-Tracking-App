package com.xieqinglin.fitness.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun TrainingSessionScreen(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, snackbar: SnackbarHostState, onExit: () -> Unit) {
    val plan = data.plan(date)
    if (plan == null || plan.exercises.isEmpty()) {
        BackHandler(onBack = onExit)
        Column(Modifier.fillMaxSize().padding(24.dp)) { EmptyState("这一天还没有训练", "先安排项目，再开始记录。"); Button(onClick = onExit) { Text("返回") } }
        return
    }
    val options by vm.comfort.collectAsStateWithLifecycle()
    val rest by vm.timer.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(view, options.keepAwake) {
        val original = view.keepScreenOn; view.keepScreenOn = options.keepAwake
        onDispose { view.keepScreenOn = original }
    }
    var selectedId by rememberSaveable(date) { mutableStateOf(TrainingCompletion.eligible(data, date).firstOrNull()?.id ?: plan.exercises.first().id) }
    val item = plan.exercises.find { it.id == selectedId } ?: plan.exercises.first()
    val record = data.execution(item.id)
    val unit = data.settings.loadUnit
    var text by rememberSaveable(item.id) { mutableStateOf(fitnessJson.encodeToString(ValuesDraft.from(record?.actual ?: item.target, unit, item.id))) }
    var saved by rememberSaveable(item.id) { mutableStateOf(text) }
    val draft = fitnessJson.decodeFromString<ValuesDraft>(text)
    var checks by rememberSaveable(item.id) { mutableStateOf(record?.completedSets.orEmpty().map { draft.rowIds()[it] }) }
    var note by rememberSaveable(item.id) { mutableStateOf(record?.note.orEmpty()) }
    var savedNote by rememberSaveable(item.id) { mutableStateOf(note) }
    var error by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var skipping by rememberSaveable { mutableStateOf(false) }
    var finishAll by rememberSaveable { mutableStateOf(false) }
    var summary by rememberSaveable { mutableStateOf(false) }
    val dirty = text != saved || note != savedNote
    val previous = data.previousExecution(item, date)
    val cue = data.settings.library.find { it.id == item.definitionId }?.cue
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun edit(next: ValuesDraft) { text = fitnessJson.encodeToString(next); error = null }
    fun exit() { if (dirty) leaving = true else onExit() }
    fun save(ids: List<String> = checks, finish: Boolean = false, after: () -> Unit = {}) {
        try {
            val actual = draft.parse(item.kind, unit, actual = true)
            val indices = draft.rowIds().mapIndexedNotNull { index, id -> index.takeIf { id in ids } }
            require(indices.all { draft.reps[it].isNotBlank() }) { "请先填写准备确认的组次数" }
            val savingText = text; val savingNote = note
            vm.saveSession(date, item, actual, indices, finish, savingNote) {
                checks = ids; saved = savingText; savedNote = savingNote; after()
            }
            error = null
        } catch (e: IllegalArgumentException) { error = e.message }
    }
    BackHandler(onBack = ::exit)
    LaunchedEffect(item.id) {
        val next = draft.rowIds().indexOfFirst { it !in checks }
        list.scrollToItem(if (item.kind == ExerciseKind.STRENGTH && next > 0) next + 2 else 0)
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Column { Text(item.name, style = MaterialTheme.typography.titleMedium); Text(date, style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = ::exit, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "退出训练") } },
            actions = { TextButton(onClick = { if (dirty) error = "先保存当前输入，再结束训练" else summary = true }, enabled = !busy) { Text("结束") } })
    }, bottomBar = {
        if (rest.running || rest.pausedMillis > 0) Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
            Box(Modifier.navigationBarsPadding()) { RestTimerCard(vm, compact = true) }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("training-list"), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(plan.exercises, key = { it.id }) { exercise -> FilterChip(exercise.id == item.id,
                        { if (dirty) error = "先保存当前输入，再切换动作" else selectedId = exercise.id }, label = { Text(exercise.name) }) }
                }
            }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.name, style = MaterialTheme.typography.headlineMedium)
                Text("目标 ${item.target.summary(item.kind, unit)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!cue.isNullOrBlank()) Text("固定提示 · $cue", color = MaterialTheme.colorScheme.secondary)
                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                if (previous != null) Text("上次 ${previous.date} · ${previous.actual.summary(previous.kindSnapshot, unit)}", style = MaterialTheme.typography.bodySmall)
                ErrorText(error)
            } }
            if (item.kind == ExerciseKind.STRENGTH) {
                items(draft.rowIds(), key = { it }) { id ->
                    val index = draft.rowIds().indexOf(id)
                    TrainingSet(draft, index, id in checks, unit, options.loadStepGrams, previous?.actual?.sets?.getOrNull(index), busy,
                        onEdit = ::edit, onCheck = {
                            val next = if (id in checks) checks - id else checks + id
                            save(next) {
                                keyboard?.hide()
                                if (id in next) {
                                    if (options.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (next.size < draft.reps.size && options.restSeconds > 0) vm.startRest()
                                    val following = draft.rowIds().indexOfFirst { it !in next }
                                    if (following >= 0) scope.launch { list.animateScrollToItem(following + 2) }
                                }
                            }
                        })
                }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { edit(draft.append(draft.reps.lastIndex)) }, enabled = !busy && draft.reps.size < 100) { Text("复制上一组") }
                    TextButton(onClick = { edit(draft.append()) }, enabled = !busy && draft.reps.size < 100) { Text("添加空白组") }
                } }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("负重步长", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
                    listOf("0.5", "1", "2.5", "5").forEach { value ->
                        val grams = Rules.weight(value, unit)
                        FilterChip(options.loadStepGrams == grams, { vm.setComfort { it.copy(loadStepGrams = grams) } }, label = { Text("$value ${unit.label}") })
                    }
                } }
            } else item { SectionCard {
                CardioTimer(item, vm, busy) { seconds -> edit(draft.copy(minutes = durationInput(seconds), exactSeconds = seconds)) }
                ValuesFields(item.kind, unit, draft, enabled = !busy, onChange = ::edit)
                runCatching { draft.parse(item.kind, unit, true).pace() }.getOrNull()?.let { Text("按记录距离与运动用时计算 · $it", style = MaterialTheme.typography.bodySmall) }
            } }
            item { SectionCard {
                OutlinedTextField(note, { note = it }, label = { Text("本次动作备注（可选）") }, maxLines = 3, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                ErrorText(error)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { save() }, enabled = !busy) { Text("保存草稿") }
                    Button(onClick = {
                        if (item.kind == ExerciseKind.STRENGTH && checks.size < draft.reps.size) finishAll = true
                        else save(finish = true)
                    }, enabled = !busy) { Text("完成动作") }
                    TextButton(onClick = { skipping = true }, enabled = !busy) { Text("跳过这个动作") }
                }
            } }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = plan.exercises.firstOrNull { it.id != item.id && data.execution(it.id)?.status !in listOf(ExecutionStatus.DONE, ExecutionStatus.SKIPPED) }
                if (next != null) Button(onClick = { if (dirty) error = "先保存当前输入" else selectedId = next.id }, enabled = !busy) { Text("下一个动作") }
                TextButton(onClick = { if (dirty) error = "先保存当前输入" else summary = true }, enabled = !busy) { Text("结束本次训练") }
                TextButton(onClick = {
                    if (dirty) error = "先保存当前输入" else vm.change("已移到最后") { current ->
                        val p = current.plan(date)!!
                        current.copy(plans = current.plans.filterNot { it.date == date } + p.copy(exercises = p.exercises.filterNot { it.id == item.id } + item))
                    }.also { next?.let { selectedId = it.id } }
                }, enabled = !busy) { Text("稍后做") }
            } }
            if (item.kind == ExerciseKind.STRENGTH && !rest.running && rest.pausedMillis == 0L) item { RestTimerCard(vm) }
        }
    }
    if (finishAll) ConfirmDialog("确认剩余训练组？", "还有 ${draft.reps.size - checks.size} 组未确认。确认后按当前数值全部完成；只做了一部分可选择结束本次训练。", "确认全部", busy,
        { finishAll = false }, { finishAll = false; save(draft.rowIds(), finish = true) })
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("保存当前输入？") }, text = { Text("已确认的组已保存。当前修改可存为草稿，下次继续。") },
        confirmButton = { TextButton(onClick = { save(after = onExit) }, enabled = !busy) { Text("保存并返回") } },
        dismissButton = { Row { TextButton(onClick = { leaving = false }) { Text("继续训练") }; TextButton(onClick = onExit, enabled = !busy) { Text("放弃本次输入") } } })
    if (skipping) ConfirmDialog("跳过 ${item.name}？", "保留填写的实际值，但清除该动作的确认标记和尚未填入的计时。", "跳过", busy, { skipping = false }) {
        try { val actual = draft.parse(item.kind, unit, true)
            vm.action("已跳过", { skipping = false; vm.cancelRest(); if (vm.activityTimer.value.exerciseId == item.id) vm.clearActivity() }) { vm.repository.saveExecution(date, item, actual, ExecutionStatus.SKIPPED, "", note = note) }
        } catch (e: IllegalArgumentException) { error = e.message; skipping = false }
    }
    if (summary) SessionSummary(data, date, vm, busy, { summary = false }) { vm.cancelRest(); onExit() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TrainingSet(draft: ValuesDraft, index: Int, checked: Boolean, unit: WeightUnit, step: Long, previous: SetValue?, busy: Boolean,
    onEdit: (ValuesDraft) -> Unit, onCheck: () -> Unit) {
    var tools by rememberSaveable(draft.rowIds()[index]) { mutableStateOf(false) }
    fun reps(value: String) = onEdit(draft.copy(reps = draft.reps.mapIndexed { i, old -> if (i == index) value else old }))
    fun load(value: String, exact: Long? = null) = onEdit(draft.copy(loads = draft.loads.mapIndexed { i, old -> if (i == index) value else old },
        exactLoads = draft.reps.indices.map { if (it == index) exact else draft.exactLoads.getOrNull(it) }))
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { onCheck() }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "完成第${index + 1}组" })
            Text("第 ${index + 1} 组", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (checked) Text("已确认", color = MaterialTheme.colorScheme.secondary)
            else TextButton(onClick = { tools = !tools }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "第${index + 1}组快捷填写" }) { Text(if (tools) "收起" else "快捷填写") }
        }
        if (checked) Text("${draft.reps[index]} 次 · ${draft.loads[index].ifBlank { "未设置" }} ${unit.label}", style = MaterialTheme.typography.titleLarge)
        else {
            AdaptiveInputPair(first = { field ->
                OutlinedTextField(draft.reps[index], { reps(it) }, label = { Text("${index + 1}组次数") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), modifier = field)
            }, second = { field ->
                OutlinedTextField(draft.loads[index], { load(it) }, label = { Text("负重 · ${unit.label}") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), modifier = field)
            })
            if (tools) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { reps(((draft.reps[index].toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString()) }, enabled = !busy) { Text("次数 −1") }
                    TextButton(onClick = { val n = draft.reps[index].toIntOrNull() ?: 0; if (n < Int.MAX_VALUE) reps((n + 1).toString()) }, enabled = !busy) { Text("次数 +1") }
                    listOf(-1, 1).forEach { direction -> TextButton(onClick = {
                        val current = draft.exactLoads.getOrNull(index)?.takeIf { formatWeight(it, unit) == draft.loads[index] }
                            ?: runCatching { Rules.weight(draft.loads[index].ifBlank { "0" }, unit, true) }.getOrNull()
                        if (current != null && current < Long.MAX_VALUE - step) {
                            val next = (current + direction * step).coerceAtLeast(0); load(formatWeight(next, unit), next)
                        }
                    }, enabled = !busy) { Text("负重 ${if (direction < 0) "−" else "+"}${formatWeight(step, unit)}") } }
                    if (index > 0) TextButton(onClick = { onEdit(draft.copyRow(index - 1, index)) }, enabled = !busy) { Text("使用上一组") }
                    if (previous != null) TextButton(onClick = { onEdit(draft.useSet(index, previous, unit)) }, enabled = !busy) { Text("使用上次") }
                    if (draft.reps.size > 1) TextButton(onClick = { onEdit(draft.remove(index)) }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "移除第${index + 1}组" }) { Text("移除本组") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CardioTimer(item: Exercise, vm: FitnessViewModel, busy: Boolean, onUse: (Double) -> Unit) {
    val clock by vm.activityTimer.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var replace by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(clock) {
        while (clock.running) {
            elapsed = SystemClock.elapsedRealtime()
            if (clock.target > 0 && clock.elapsed(elapsed) >= clock.target) { vm.pauseActivity(); break }
            delay(250)
        }
        elapsed = SystemClock.elapsedRealtime()
    }
    Text("运动计时", style = MaterialTheme.typography.titleMedium)
    if (clock.exerciseId == item.id) {
        Text(clockLabel(if (clock.target > 0) clock.target - clock.elapsed(elapsed) else clock.elapsed(elapsed)), style = MaterialTheme.typography.headlineLarge)
        Text(if (clock.target > 0) "目标倒计时 · 已运动 ${clockLabel(clock.elapsed(elapsed))}" else "正计时 · 暂停时间不累计", style = MaterialTheme.typography.bodySmall)
        if (vm.activityInterrupted) Text("设备曾重启，请核对当前用时。", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (clock.running) vm.pauseActivity() else vm.resumeActivity() }, enabled = !busy && (clock.target == 0L || clock.elapsed(elapsed) < clock.target)) { Text(if (clock.running) "暂停运动" else "继续计时") }
            Button(onClick = { vm.pauseActivity(); onUse(clock.elapsed(SystemClock.elapsedRealtime()).div(1000).toDouble()) }, enabled = !busy) { Text("填入用时") }
            TextButton(onClick = { replace = true }, enabled = !busy) { Text("重置计时") }
        }
    } else if (clock.exerciseId.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.startActivity(item.id) }, enabled = !busy) { Text("开始正计时") }
        item.target.seconds?.let { seconds -> OutlinedButton(onClick = { vm.startActivity(item.id, seconds) }, enabled = !busy) { Text("按目标倒计时") } }
    } else {
        Text("另一个项目还有计时，请先核对再重置。")
        TextButton(onClick = { replace = true }, enabled = !busy) { Text("清除旧计时") }
    }
    Text("计时不会自动完成项目，填入后请核对并保存。", style = MaterialTheme.typography.bodySmall)
    if (replace) ConfirmDialog("清除当前运动计时？", "已保存的实际记录不受影响。未填入的计时会清除。", "清除", busy, { replace = false }) { vm.clearActivity(); replace = false }
}
