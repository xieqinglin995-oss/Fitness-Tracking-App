package com.xieqinglin.fitness.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import java.time.LocalDate

@Composable fun WorkoutCard(item: Exercise, date: String, data: FitnessData, vm: FitnessViewModel, busy: Boolean,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null, onExecute: (() -> Unit)? = null) {
    val record = data.execution(item.id)
    var execute by rememberSaveable(item.id) { mutableStateOf(false) }
    val unit = data.settings.unit
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (record?.status) { ExecutionStatus.DONE -> Icons.Outlined.CheckCircle; ExecutionStatus.SKIPPED -> Icons.Outlined.SkipNext; else -> Icons.Outlined.FitnessCenter }, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(item.kind.label + if (item.temporary) " · 临时新增" else "", style = MaterialTheme.typography.labelMedium)
            }
            Text(when (record?.status) { ExecutionStatus.DONE -> "已完成"; ExecutionStatus.SKIPPED -> "已跳过"; ExecutionStatus.DRAFT -> "进行中"; else -> "待完成" }, style = MaterialTheme.typography.labelLarge)
        }
        Text("计划：${item.target.description(item.kind, unit)}")
        if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
        if (record != null) {
            HorizontalDivider()
            Text("实际：${record.actual.description(record.kindSnapshot, unit)}", color = MaterialTheme.colorScheme.primary)
            if (record.nameSnapshot != item.name) Text("记录时项目：${record.nameSnapshot}", style = MaterialTheme.typography.bodySmall)
            if (record.targetSnapshot != item.target) Text("记录时目标：${record.targetSnapshot.description(record.kindSnapshot, unit)}", style = MaterialTheme.typography.bodySmall)
            if (record.status == ExecutionStatus.SKIPPED) Text("跳过原因：${record.skipReason.ifBlank { "未填写" }}")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (onUp != null) IconButton(onClick = onUp, enabled = !busy) { Icon(Icons.Outlined.ArrowUpward, "上移项目") }
            if (onDown != null) IconButton(onClick = onDown, enabled = !busy) { Icon(Icons.Outlined.ArrowDownward, "下移项目") }
            if (onEdit != null) IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Outlined.Edit, "编辑计划目标") }
            if (onDelete != null) IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Outlined.DeleteOutline, "删除计划项目") }
        }
        OutlinedButton(onClick = { if (onExecute != null) onExecute() else execute = true }, enabled = !busy && date <= LocalDate.now().toString(), modifier = Modifier.fillMaxWidth()) {
            Text(if (date > LocalDate.now().toString()) "未来日期不能记录实际训练" else if (record == null) "记录实际 / 完成 / 跳过" else "修正实际 / 撤销完成")
        }
    }
    if (execute) ExecutionForm(date, item, record, vm, unit, busy) { execute = false }
}

@Composable fun PlanScreen(data: FitnessData, date: String, onDate: (String) -> Unit, today: LocalDate, vm: FitnessViewModel, busy: Boolean) {
    val selected = LocalDate.parse(date)
    val monday = weekStart(selected)
    val plan = data.plan(date)
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var restConfirm by rememberSaveable { mutableStateOf(false) }
    var templatesOpen by rememberSaveable { mutableStateOf(false) }
    var templateSave by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var transfer by rememberSaveable { mutableStateOf<String?>(null) }
    var copyWeek by rememberSaveable { mutableStateOf(false) }
    fun reorder(id: String, delta: Int) = vm.change { current ->
        current.copy(plans = current.plans.map { p -> if (p.date != date) p else {
            val items = p.exercises.toMutableList(); val index = items.indexOfFirst { it.id == id }; val target = index + delta
            if (index >= 0 && target in items.indices) { val item = items.removeAt(index); items.add(target, item) }
            p.copy(exercises = items)
        } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(selected.minusWeeks(1).toString()) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                Text("${monday} — ${monday.plusDays(6)}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onDate(selected.plusWeeks(1).toString()) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onDate(today.toString()) }) { Text("回到今天") }
                TextButton(onClick = { copyWeek = true }, enabled = !busy) { Text("复制本周") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0L..6L).forEach { offset ->
                    val day = monday.plusDays(offset)
                    val status = data.dayStatus(day.toString())
                    val label = when (status) { DayStatus.EMPTY -> "未安排"; DayStatus.REST -> "休息日"; else -> "训练日" }
                    FilterChip(day == selected, { onDate(day.toString()) }, label = {
                        Column(Modifier.widthIn(min = 48.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(listOf("一", "二", "三", "四", "五", "六", "日")[offset.toInt()])
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    })
                }
            }
        }
        item { SectionCard {
            Text("$date · ${data.dayStatus(date).label}", color = MaterialTheme.colorScheme.primary)
            if (plan?.exercises?.isNotEmpty() == true) Text("完成 ${plan.exercises.count { data.execution(it.id)?.status == ExecutionStatus.DONE }} 项 · 跳过 ${plan.exercises.count { data.execution(it.id)?.status == ExecutionStatus.SKIPPED }} 项 · 共 ${plan.exercises.size} 项", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(plan?.name ?: "安排这一天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (plan != null && !plan.rest) IconButton(onClick = { rename = true }, enabled = !busy) { Icon(Icons.Outlined.Edit, "修改计划名称") }
            }
            if (plan?.rest == true) Text("已安排休息。添加训练或应用模板会将这天改为训练日。")
            if (plan == null) Text("从一个动作开始，也可以复用自己的训练模板。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addOpen = true }, enabled = !busy) { Text("＋ 添加项目") }
                OutlinedButton(onClick = { templatesOpen = true }, enabled = !busy) { Text("训练模板") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { restConfirm = true }, enabled = !busy) { Text(if (plan?.rest == true) "取消休息日" else "设为休息日") }
                if (plan != null) {
                    TextButton(onClick = { transfer = "copy" }, enabled = !busy) { Text("复制到…") }
                    TextButton(onClick = { transfer = "move" }, enabled = !busy) { Text("改期…") }
                }
            }
            if (plan?.exercises?.isNotEmpty() == true) TextButton(onClick = { templateSave = true }, enabled = !busy) { Text("另存为训练模板") }
        } }
        plan?.exercises.orEmpty().forEachIndexed { index, exercise -> item(key = exercise.id) {
            WorkoutCard(exercise, date, data, vm, busy,
                onEdit = { editId = exercise.id }, onDelete = { deleteId = exercise.id },
                onUp = if (index > 0) ({ reorder(exercise.id, -1) }) else null,
                onDown = if (index < plan!!.exercises.lastIndex) ({ reorder(exercise.id, 1) }) else null)
        } }
    }
    val editing = plan?.exercises?.find { it.id == editId }
    if (addOpen || editing != null) ExerciseForm(editing, data.settings.unit, busy, onDismiss = { addOpen = false; editId = null }) { item ->
        vm.action(onSuccess = { addOpen = false; editId = null }) { vm.repository.saveExercise(date, item) }
    }
    deleteId?.let { id ->
        val recorded = data.execution(id) != null
        ConfirmDialog(if (recorded) "保留实际训练记录" else "删除计划项目？",
            if (recorded) "该项目已有实际训练记录。为保留历史，此处不会删除项目。若确需删除，请先进入“修正实际”明确删除实际记录，再删除项目。" else "将删除这个项目的计划目标。",
            if (recorded) "知道了" else "删除", busy, { deleteId = null }, {
                if (recorded) deleteId = null else vm.change("项目已删除", { deleteId = null }) { current ->
                    require(current.execution(id) == null) { "项目已有实际记录，不能删除" }
                    current.copy(plans = current.plans.map { if (it.date == date) it.copy(exercises = it.exercises.filterNot { e -> e.id == id }) else it })
                }
            })
    }
    if (restConfirm) ConfirmDialog(if (plan?.rest == true) "取消休息日？" else "设为休息日？",
        if (plan?.rest == true) "取消后恢复为未安排。" else "将移除当天 ${plan?.exercises?.size ?: 0} 个计划项目；有实际记录时会阻止此操作，保留历史。", busy = busy,
        onDismiss = { restConfirm = false }, onConfirm = { vm.change(onSuccess = { restConfirm = false }) { PlanOperations.rest(it, date, plan?.rest != true) } })
    if (rename) NameDialog("计划名称", plan?.name ?: "当日训练", busy, { rename = false }) { name ->
        vm.change(onSuccess = { rename = false }) { it.copy(plans = it.plans.map { p -> if (p.date == date) p.copy(name = name) else p }) }
    }
    if (templateSave) NameDialog("另存为模板", plan?.name ?: "我的模板", busy, { templateSave = false }) { name ->
        vm.change("模板已保存", { templateSave = false }) { current ->
            val items = requireNotNull(current.plan(date)).exercises
            current.copy(templates = current.templates + WorkoutTemplate(name = name, exercises = items.map { it.freshCopy() }))
        }
    }
    if (templatesOpen) TemplateManager(data, date, vm, busy) { templatesOpen = false }
    if (transfer != null && plan != null) TransferDialog(data, plan, transfer == "move", vm, busy) { transfer = null }
    if (copyWeek) CopyWeekDialog(data, monday, vm, busy) { copyWeek = false }
}

@Composable fun NameDialog(title: String, initial: String, busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    FormDialog(title, busy, onDismiss) {
        OutlinedTextField(name, { name = it }, label = { Text("名称（1–100 字）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = { onSave(name.trim()) }, enabled = !busy && name.trim().length in 1..100) { Text("保存") }
        }
    }
}
