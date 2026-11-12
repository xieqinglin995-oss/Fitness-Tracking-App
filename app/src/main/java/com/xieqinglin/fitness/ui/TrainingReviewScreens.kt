package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SessionSummary(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit, onEnded: () -> Unit) {
    val day = LocalDate.parse(date)
    val review = data.review(day, day)
    val plan = data.plan(date)
    val clock by vm.activityTimer.collectAsStateWithLifecycle()
    val clockForDay = plan?.exercises.orEmpty().any { it.id == clock.exerciseId }
    val unfiledClock = clockForDay && (clock.running || data.execution(clock.exerciseId)?.actual?.seconds != clock.elapsed(android.os.SystemClock.elapsedRealtime()).div(1000).toDouble())
    val records = plan?.exercises.orEmpty().mapNotNull { data.execution(it.id) }
    val done = records.count { it.status == ExecutionStatus.DONE }
    val skipped = records.count { it.status == ExecutionStatus.SKIPPED }
    val pending = plan?.exercises.orEmpty().size - done - skipped
    var note by rememberSaveable(date) { mutableStateOf(data.settings.sessions.find { it.date == date }?.note.orEmpty()) }
    var templates by rememberSaveable { mutableStateOf(false) }
    FormDialog("本次训练 · $date", busy, onDismiss, dirty = note != data.settings.sessions.find { it.date == date }?.note.orEmpty()) {
        Text("完成 $done 项 · 跳过 $skipped 项 · 未完成 $pending 项", style = MaterialTheme.typography.titleMedium)
        Text("已确认 ${review.sets} 组；有氧 ${formatNumber(review.kilometers)} km · ${durationInput(review.seconds)} 分:秒")
        Text("未完成内容会保留原状态。结束不会自动确认剩余组。", style = MaterialTheme.typography.bodySmall)
        if (unfiledClock) Text("还有运动计时未核对。请返回动作暂停、填入用时并保存，或明确清除计时后再结束。", color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(plan?.exercises.orEmpty(), key = { it.id }) { item ->
                val record = data.execution(item.id)
                Text("${item.name} · ${when (record?.status) { ExecutionStatus.DONE -> "已完成"; ExecutionStatus.SKIPPED -> "已跳过"; else -> "未完成" }}" +
                    if (item.kind == ExerciseKind.STRENGTH) " · 已确认 ${record?.completedSets?.size ?: 0} 组" else record?.actual?.let { " · ${it.summary(item.kind, data.settings.loadUnit)}" }.orEmpty())
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("本次总结（可选，最多 500 字）") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
        if (records.any { it.status == ExecutionStatus.DONE || it.completedSets.isNotEmpty() }) OutlinedButton(onClick = { templates = true }, enabled = !busy) { Text("预览采用实际值更新模板") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("继续训练") }
            Button(onClick = { vm.endSession(date, note) { if (clockForDay) vm.clearActivity(); onEnded() } }, enabled = !busy && !unfiledClock) { Text(if (pending > 0) "保留部分并结束" else "保留记录并结束") }
        }
    }
    if (templates) ActualTemplatePreview(data, date, vm, busy) { templates = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ActualTemplatePreview(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val plan = data.plan(date) ?: return
    var templateId by rememberSaveable { mutableStateOf(data.settings.sources.find { it.date == date }?.templateId ?: data.templates.firstOrNull()?.id) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val unit = data.settings.loadUnit
    fun target(item: Exercise): Values? {
        val record = data.execution(item.id) ?: return null
        return if (item.kind == ExerciseKind.STRENGTH) record.actual.copy(sets = record.completedSets.map { record.actual.sets[it] }.filter { it.reps > 0 }).takeIf { it.sets.isNotEmpty() }
        else record.actual.takeIf { record.status == ExecutionStatus.DONE && runCatching { Rules.values(item.kind, it) }.isSuccess }
    }
    FormDialog("预览模板变化", busy, onDismiss) {
        if (data.templates.isEmpty()) Text("请先在计划页将常用安排另存为模板。")
        else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { data.templates.forEach { template -> FilterChip(templateId == template.id, { templateId = template.id }, label = { Text(template.name) }) } }
            val template = data.templates.find { it.id == templateId }
            Text("原模板", style = MaterialTheme.typography.titleSmall)
            Text(template?.exercises?.joinToString("\n") { "${it.name} · ${it.target.summary(it.kind, unit)}" }.orEmpty())
            Text("新模板将采用今天的项目和顺序。仅勾选的项目使用已确认实际，其余沿用今天的计划目标。历史和其他日期不变。", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(plan.exercises, key = { it.id }) { item ->
                    val actual = target(item)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.id in selected, { selected = if (item.id in selected) selected - item.id else selected + item.id }, enabled = !busy && actual != null)
                        Column { Text(item.name); Text("${item.target.summary(item.kind, unit)} → ${(if (item.id in selected) actual else item.target)?.summary(item.kind, unit)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            ErrorText(error)
            Button(onClick = {
                if (selected.isEmpty() || template == null) error = "请选模板和至少一个可采用实际值的项目"
                else vm.change("模板已更新，历史未修改", onDismiss) { current ->
                    val live = current.plan(date) ?: error("当天计划已变更")
                    val changed = template.copy(exercises = live.exercises.map { item -> item.copy(id = newId(), temporary = false,
                        target = (if (item.id in selected) target(item) ?: item.target else item.target).let { v -> v.copy(sets = v.sets.map { it.copy(id = newId()) }) }) })
                    current.copy(templates = current.templates.map { if (it.id == changed.id) changed else it })
                }
            }, enabled = !busy) { Text("确认替换所选模板") }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("仅保留本次，不改模板") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun RepeatPlanDialog(data: FitnessData, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var templateId by rememberSaveable { mutableStateOf(data.templates.firstOrNull()?.id) }
    var start by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf(LocalDate.now().plusWeeks(4).minusDays(1).toString()) }
    var weekdays by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var choices by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    val batch = rememberSaveable { newId() }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val dates = runCatching { RepeatingPlans.dates(LocalDate.parse(start), LocalDate.parse(end), weekdays.toSet()) }.getOrNull()
    FormDialog("周期排期", busy, onDismiss) {
        Text("从自己的模板生成最多 12 周的独立计划。不复制完成状态，不自动改动已开始的日期。", style = MaterialTheme.typography.bodySmall)
        if (data.templates.isEmpty()) Text("先在训练模板中新建或另存一个常用安排。")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { data.templates.forEach { t -> FilterChip(templateId == t.id, { templateId = t.id; preview = false }, label = { Text(t.name) }) } }
        DateField(start, { start = it; preview = false }, "开始日期")
        DateField(end, { end = it; preview = false }, "结束日期")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, text ->
            val day = i + 1
            FilterChip(day in weekdays, { weekdays = if (day in weekdays) weekdays - day else weekdays + day; preview = false }, label = { Text(text) })
        } }
        ErrorText(error)
        if (!preview) Button(onClick = {
            if (dates.isNullOrEmpty() || templateId == null) error = "请选择模板、有效起止日期和至少一个星期（最长 12 周）"
            else { preview = true; error = null }
        }, enabled = !busy) { Text("预览具体日期") }
        else {
            Text("共 ${dates?.size ?: 0} 个日期；冲突默认跳过。", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(dates.orEmpty(), key = { it.toString() }) { day ->
                    val date = day.toString(); val existing = data.plan(date)
                    val protected = existing?.exercises.orEmpty().any { data.execution(it.id) != null }
                    Column {
                        Text("$date · ${if (existing == null) "新建" else if (protected) "已有实际，仅保留" else "已有计划"}")
                        if (existing != null && !protected) FlowRow { listOf(ConflictChoice.SKIP to "保留原计划", ConflictChoice.REPLACE to "替换目标").forEach { (choice, label) ->
                            FilterChip((choices[date] ?: "SKIP") == choice.name, { choices = choices + (date to choice.name) }, label = { Text(label) })
                        } }
                    }
                }
            }
            Button(onClick = { val template = data.templates.find { it.id == templateId }
                if (template != null && dates != null) vm.change("周期计划已生成", onDismiss) { RepeatingPlans.apply(it, template, dates, choices.mapValues { e -> ConflictChoice.valueOf(e.value) }, batch) }
            }, enabled = !busy) { Text("确认生成") }
        }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TrainingHistory(data: FitnessData, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit, onDate: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var definitionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val week = remember(data, today) { data.review(weekStart(today), today) }
    val rows = remember(data, query, kind, definitionFilter) { data.executions.filter { record ->
        (definitionFilter == null || data.item(record.exerciseId)?.definitionId == definitionFilter) &&
        (kind == null || record.kindSnapshot.name == kind) && (record.nameSnapshot.contains(query, true) || record.note.contains(query, true) || record.date.contains(query))
    }.sortedByDescending { it.date } }
    FormDialog("训练回看", busy, onDismiss) {
        Text("本周 ${week.days} 天 · 力量 ${week.strengthDays} 天 / ${week.sets} 组 · 有氧 ${week.cardioDays} 天", style = MaterialTheme.typography.titleMedium)
        Text("同一天两类训练只算一个训练日期；跳过与没有确认记录的草稿不计入。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, label = { Text("搜索动作、日期或本次备注") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (definitionFilter != null) TextButton(onClick = { definitionFilter = null }) { Text("已限定同一动作 · 查看全部") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(kind == null, { kind = null }, label = { Text("全部") })
            ExerciseKind.entries.forEach { k -> FilterChip(kind == k.name, { kind = k.name }, label = { Text(k.label) }) }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.id }) { record ->
                Column {
                    Text("${record.date} · ${record.nameSnapshot}", style = MaterialTheme.typography.titleMedium)
                    Text(when (record.status) { ExecutionStatus.DONE -> "已完成"; ExecutionStatus.SKIPPED -> "已跳过"; else -> "草稿 / 进行中" })
                    if (record.kindSnapshot == ExerciseKind.STRENGTH) Text("已确认 ${record.completedSets.size}/${record.actual.sets.size} 组 · " + record.completedSets.joinToString("、") { i -> "${record.actual.sets[i].reps}次" })
                    else Text(record.actual.summary(record.kindSnapshot, data.settings.loadUnit) + record.actual.pace()?.let { " · $it" }.orEmpty())
                    if (record.note.isNotBlank()) Text(record.note, style = MaterialTheme.typography.bodySmall)
                    FlowRow { TextButton(onClick = { onDate(record.date); onDismiss() }) { Text("查看当天") }
                        TextButton(onClick = { selected = record.exerciseId }) { Text("动作提示与归集") }
                        data.item(record.exerciseId)?.definitionId?.let { id -> TextButton(onClick = { definitionFilter = id; query = "" }) { Text("只看此动作") } }
                    }
                }
            }
            if (rows.isEmpty()) item { Text("暂无匹配的实际记录") }
        }
        TextButton(onClick = onDismiss) { Text("关闭") }
    }
    data.item(selected.orEmpty())?.let { item -> DefinitionEditor(data, item, vm, busy) { selected = null } }
}

@Composable private fun DefinitionEditor(data: FitnessData, item: Exercise, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val definition = data.settings.library.find { it.id == item.definitionId }
    var cue by rememberSaveable { mutableStateOf(definition?.cue.orEmpty()) }
    val candidates = remember(data, item) { data.plans.flatMap { plan -> plan.exercises.filter { it.kind == item.kind && it.name == item.name && (it.definitionId == null || it.definitionId == item.definitionId) }.map { plan.date to it } } }
    var ids by rememberSaveable { mutableStateOf(listOf(item.id)) }
    FormDialog("${item.name} · 动作身份", busy, onDismiss, dirty = cue != definition?.cue.orEmpty()) {
        OutlinedTextField(cue, { cue = it }, label = { Text("固定提示（例如座椅档位）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Text("勾选你确认属于同一动作的日期。器械或重量口径不同请勿归并；不改当时名称与实际数值。", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) { items(candidates, key = { it.second.id }) { (date, exercise) ->
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(exercise.id in ids, { ids = if (exercise.id in ids) ids - exercise.id else ids + exercise.id }, enabled = !busy)
                Text("$date · ${exercise.target.summary(exercise.kind, data.settings.loadUnit)}") }
        } }
        Button(onClick = { vm.change("动作提示与归集已保存", onDismiss) { current ->
            require(ids.isNotEmpty()) { "请至少选择一项" }
            val id = item.definitionId ?: newId()
            current.copy(plans = current.plans.map { p -> p.copy(exercises = p.exercises.map { if (it.id in ids) it.copy(definitionId = id) else it }) },
                settings = current.settings.copy(library = current.settings.library.filterNot { it.id == id } + ExerciseDefinition(id, item.name, item.kind, cue)))
        } }, enabled = !busy) { Text("确认归集并保存提示") }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun UpdateBatchDialog(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    val source = data.settings.sources.find { it.date == date } ?: return
    var templateId by rememberSaveable { mutableStateOf(source.templateId) }
    val candidates = data.settings.sources.filter { it.batchId == source.batchId && it.date >= LocalDate.now().toString() && data.plan(it.date) != null }.sortedBy { it.date }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    FormDialog("更新未来计划目标", busy, onDismiss) {
        Text("只更新本批次中你选中的未来未开始日期。已经有实际记录的日期保留，过去日期不变。", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { data.templates.forEach { template -> FilterChip(templateId == template.id, { templateId = template.id }, label = { Text(template.name) }) } }
        data.templates.find { it.id == templateId }?.let { template -> Text("新目标：" + template.exercises.joinToString("；") { "${it.name} ${it.target.summary(it.kind, data.settings.loadUnit)}" }, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) { items(candidates, key = { it.date }) { candidate ->
            val p = data.plan(candidate.date)!!; val protected = p.exercises.any { data.execution(it.id) != null }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(candidate.date in selected, { selected = if (candidate.date in selected) selected - candidate.date else selected + candidate.date }, enabled = !busy && !protected)
                Text("${candidate.date} · ${p.name}" + if (protected) "（已有实际，保留）" else "")
            }
        } }
        Button(onClick = { data.templates.find { it.id == templateId }?.let { template -> vm.change("所选未来计划已更新", onDismiss) {
            RepeatingPlans.updateFuture(it, source.batchId, template, selected.toSet())
        } } }, enabled = !busy && selected.isNotEmpty()) { Text("确认仅更新所选日期") }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
    }
}
