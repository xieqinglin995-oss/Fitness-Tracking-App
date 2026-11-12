package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.LocalDate

@Composable fun WeightForm(data: FitnessData, original: WeightEntry?, initialDate: String, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var unitName by rememberSaveable { mutableStateOf(data.settings.unit.name) }
    val unit = WeightUnit.valueOf(unitName)
    var date by rememberSaveable { mutableStateOf(original?.date ?: initialDate) }
    var number by rememberSaveable { mutableStateOf(original?.let { formatWeight(it.grams, unit) } ?: "") }
    var exactGrams by rememberSaveable { mutableStateOf(original?.grams) }
    var note by rememberSaveable { mutableStateOf(original?.note ?: "") }
    var details by rememberSaveable { mutableStateOf(initialDate != LocalDate.now().toString() || original?.note?.isNotBlank() == true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var overwrite by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    fun save(allowOverwrite: Boolean) {
        try {
            Rules.pastDate(date)
            val grams = WeightInput(number, unit, exactGrams).grams()
            require(note.length <= 500) { "备注不能超过 500 字" }
            if (data.weights.any { it.date == date && it.id != original?.id } && !allowOverwrite) { overwrite = true; return }
            val entry = WeightEntry(original?.id ?: newId(), date, grams, note, original?.createdAt ?: now())
            vm.action(onSuccess = onDismiss) { vm.repository.saveWeight(entry, original?.id, allowOverwrite, unit) }
        } catch (e: IllegalArgumentException) { error = e.message }
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    FormDialog(if (original == null) "记录体重" else "编辑体重", busy, onDismiss) {
        LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
        Text(if (date == LocalDate.now().toString()) "今天 · $date" else date, color = MaterialTheme.colorScheme.onSurfaceVariant)
        UnitSelector(unit, !busy) { next ->
            try {
                val converted = WeightInput(number, unit, exactGrams).switchTo(next)
                unitName = converted.unit.name; number = converted.text; exactGrams = converted.exactGrams; error = null
            } catch (e: IllegalArgumentException) { error = "请先修正数值，再切换单位：${e.message}" }
        }
        OutlinedTextField(number, { number = it; exactGrams = null; error = null }, label = { Text("体重 · ${unit.label}") }, singleLine = true,
            textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!busy) save(false) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus))
        Text(WeightInput(number, unit, exactGrams).equivalent()?.let { "换算为 $it · 保存后记住所选体重单位" }
            ?: "1 公斤 = 2 斤 · 切换单位自动换算数值", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { details = !details }, enabled = !busy) { Text(if (details) "收起日期与备注" else "修改日期 / 添加备注") }
        if (details) {
            DateField(date, { date = it; error = null }, allowFuture = false)
            OutlinedTextField(note, { note = it }, label = { Text("备注（可选，最多 500 字）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        }
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (original != null) TextButton(onClick = { deleting = true }, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = { save(false) }, enabled = !busy) { Text(if (busy) "保存中…" else "保存") }
        }
    }
    if (overwrite) ConfirmDialog("覆盖该日期体重？", "$date 已有 ${data.weights.find { it.date == date }?.let { formatWeight(it.grams, unit) }} ${unit.label}，确认后将替换为 $number ${unit.label}。", "覆盖", busy,
        { overwrite = false }, { overwrite = false; save(true) })
    if (deleting && original != null) ConfirmDialog("删除体重记录？", "将删除 ${original.date} 的记录，图表与统计会同步更新。", "删除", busy,
        { deleting = false }, { vm.change("已删除", onDismiss) { it.copy(weights = it.weights.filterNot { w -> w.id == original.id }) } })
}

@Serializable private data class ValuesDraft(
    val reps: List<String> = listOf("10"), val loads: List<String> = listOf(""),
    val minutes: String = "", val km: String = "",
) {
    fun parse(kind: ExerciseKind, unit: WeightUnit, actual: Boolean): Values {
        val value = when (kind) {
            ExerciseKind.STRENGTH -> Values(sets = reps.mapIndexed { i, text ->
                val count = text.toIntOrNull()
                require(count != null && Regex("[0-9]+").matches(text)) { "第 ${i + 1} 组次数须为整数" }
                SetValue(count, loads[i].takeIf { it.isNotBlank() }?.let { Rules.weight(it, unit, true) })
            })
            ExerciseKind.DURATION -> Values(seconds = minutes.toDoubleOrNull()?.times(60))
            ExerciseKind.DISTANCE -> {
                require(minutes.isBlank() || minutes.toDoubleOrNull() != null) { "请输入有效时长" }
                Values(seconds = minutes.takeIf { it.isNotBlank() }?.toDoubleOrNull()?.times(60), kilometers = km.toDoubleOrNull())
            }
        }
        Rules.values(kind, value, actual)
        return value
    }
    companion object {
        fun from(values: Values?, unit: WeightUnit) = ValuesDraft(
            reps = values?.sets?.takeIf { it.isNotEmpty() }?.map { it.reps.toString() } ?: listOf("10"),
            loads = values?.sets?.takeIf { it.isNotEmpty() }?.map { it.loadGrams?.let { g -> formatWeight(g, unit) } ?: "" } ?: listOf(""),
            minutes = values?.seconds?.let { formatNumber(it / 60) } ?: "",
            km = values?.kilometers?.let(::formatNumber) ?: "",
        )
    }
}

@Composable private fun ValuesFields(kind: ExerciseKind, unit: WeightUnit, draft: ValuesDraft, onChange: (ValuesDraft) -> Unit) {
    when (kind) {
        ExerciseKind.STRENGTH -> {
            Text("逐组填写 · 空白负重表示未设置，0 表示不额外负重", style = MaterialTheme.typography.bodySmall)
            draft.reps.forEachIndexed { index, value ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value, { text -> onChange(draft.copy(reps = draft.reps.mapIndexed { i, s -> if (i == index) text else s })) },
                        label = { Text("${index + 1}组次数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(draft.loads[index], { text -> onChange(draft.copy(loads = draft.loads.mapIndexed { i, s -> if (i == index) text else s })) },
                        label = { Text("负重 · ${unit.label}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    if (draft.reps.size > 1) TextButton(onClick = { onChange(draft.copy(reps = draft.reps.filterIndexed { i, _ -> i != index }, loads = draft.loads.filterIndexed { i, _ -> i != index })) }, modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 56.dp)) { Text("删") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onChange(draft.copy(reps = draft.reps + draft.reps.first(), loads = draft.loads + draft.loads.first())) }, enabled = draft.reps.size < 100) { Text("＋ 添加一组") }
                TextButton(onClick = { onChange(draft.copy(reps = List(draft.reps.size) { draft.reps.first() }, loads = List(draft.loads.size) { draft.loads.first() })) }) { Text("首组复制到全部") }
            }
        }
        ExerciseKind.DURATION -> OutlinedTextField(draft.minutes, { onChange(draft.copy(minutes = it)) }, label = { Text("时长 · 分钟") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        ExerciseKind.DISTANCE -> {
            OutlinedTextField(draft.km, { onChange(draft.copy(km = it)) }, label = { Text("距离 · km") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.minutes, { onChange(draft.copy(minutes = it)) }, label = { Text("时长 · 分钟（可选）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable fun ExerciseForm(original: Exercise?, unit: WeightUnit, busy: Boolean, temporary: Boolean = false, onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var kindName by rememberSaveable { mutableStateOf((original?.kind ?: ExerciseKind.STRENGTH).name) }
    val kind = ExerciseKind.valueOf(kindName)
    var note by rememberSaveable { mutableStateOf(original?.note ?: "") }
    var draftText by rememberSaveable { mutableStateOf(fitnessJson.encodeToString(ValuesDraft.from(original?.target, unit))) }
    val draft = fitnessJson.decodeFromString<ValuesDraft>(draftText)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    FormDialog(if (original == null) "添加训练项目" else "编辑计划目标", busy, onDismiss) {
        OutlinedTextField(name, { name = it }, label = { Text("项目名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (name.isBlank()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("深蹲", "拉伸", "跑步").forEach { suggestion -> SuggestionChip(onClick = { name = suggestion }, label = { Text(suggestion) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseKind.entries.forEach { type -> FilterChip(kind == type, { kindName = type.name }, label = { Text(type.label) }) }
        }
        ValuesFields(kind, unit, draft) { draftText = fitnessJson.encodeToString(it); error = null }
        OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = {
                try {
                    val exercise = Exercise(original?.id ?: newId(), name.trim(), kind, draft.parse(kind, unit, false), note, original?.temporary ?: temporary)
                    Rules.exercise(exercise)
                    onSave(exercise)
                } catch (e: IllegalArgumentException) { error = e.message }
            }, enabled = !busy) { Text("保存项目") }
        }
    }
}

@Composable fun ExecutionForm(date: String, item: Exercise, existing: Execution?, vm: FitnessViewModel, unit: WeightUnit, busy: Boolean, onDismiss: () -> Unit) {
    var draftText by rememberSaveable { mutableStateOf(fitnessJson.encodeToString(ValuesDraft.from(existing?.actual ?: item.target, unit))) }
    var statusName by rememberSaveable { mutableStateOf((existing?.status ?: ExecutionStatus.DRAFT).name) }
    var reason by rememberSaveable { mutableStateOf(existing?.skipReason ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val draft = fitnessJson.decodeFromString<ValuesDraft>(draftText)
    val status = ExecutionStatus.valueOf(statusName)
    val screen by vm.state.collectAsStateWithLifecycle()
    val previous = screen.data.previousExecution(item, date)
    FormDialog("记录实际 · ${existing?.nameSnapshot ?: item.name}", busy, onDismiss) {
        Text("训练归属日期：$date", style = MaterialTheme.typography.titleSmall)
        Text("计划目标：${item.target.description(item.kind, unit)}", style = MaterialTheme.typography.bodyMedium)
        if (existing != null && existing.targetSnapshot != item.target) Text("开始记录时目标：${existing.targetSnapshot.description(existing.kindSnapshot, unit)}")
        Text(if (existing == null) "下方实际值默认使用计划目标，请核对或修改，再明确选择状态。" else "实际与目标分别保存，可修正或撤销完成状态。", style = MaterialTheme.typography.bodySmall)
        if (existing == null && previous != null) {
            Text("上次 ${previous.date}：${previous.actual.summary(previous.kindSnapshot, unit)}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { draftText = fitnessJson.encodeToString(ValuesDraft.from(previous.actual, unit)); error = null }, enabled = !busy) { Text("使用上次实际") }
        }
        ValuesFields(item.kind, unit, draft) { draftText = fitnessJson.encodeToString(it); error = null }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ExecutionStatus.DRAFT to "进行中", ExecutionStatus.DONE to "完成", ExecutionStatus.SKIPPED to "跳过").forEach { (s, label) ->
                FilterChip(status == s, { statusName = s.name }, label = { Text(label) })
            }
        }
        if (status == ExecutionStatus.SKIPPED) OutlinedTextField(reason, { reason = it }, label = { Text("跳过原因（可选）") }, modifier = Modifier.fillMaxWidth())
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (existing != null) TextButton(onClick = { deleting = true }, enabled = !busy) { Text("删除实际") }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = {
                try {
                    Rules.pastDate(date)
                    val value = draft.parse(item.kind, unit, true)
                    require(reason.length <= 500) { "跳过原因不能超过 500 字" }
                    vm.action(onSuccess = onDismiss) { vm.repository.saveExecution(date, item, value, status, reason) }
                } catch (e: IllegalArgumentException) { error = e.message }
            }, enabled = !busy) { Text("确认保存") }
        }
    }
    if (deleting && existing != null) ConfirmDialog("删除实际训练记录？", "删除 $date 的该项目实际值与完成状态，计划目标会保留。", "删除", busy,
        { deleting = false }, { vm.change("实际记录已删除", onDismiss) { it.copy(executions = it.executions.filterNot { e -> e.id == existing.id }) } })
}
