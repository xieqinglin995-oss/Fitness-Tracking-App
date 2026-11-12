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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import java.time.LocalDate
import com.xieqinglin.fitness.backup.fitnessJson
import kotlinx.serialization.encodeToString

@Composable fun WorkoutCard(item: Exercise, date: String, data: FitnessData, vm: FitnessViewModel, busy: Boolean,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null, onExecute: (() -> Unit)? = null) {
    val record = data.execution(item.id)
    var execute by rememberSaveable(item.id) { mutableStateOf(false) }
    val unit = data.settings.loadUnit
    val previous = data.previousExecution(item, date)
    var menu by rememberSaveable { mutableStateOf(false) }
    var quickComplete by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(when (record?.status) { ExecutionStatus.DONE -> Icons.Outlined.CheckCircle; ExecutionStatus.SKIPPED -> Icons.Outlined.SkipNext; else -> Icons.Outlined.FitnessCenter }, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(item.kind.label + if (item.temporary) " · 临时新增" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(when (record?.status) { ExecutionStatus.DONE -> "已完成"; ExecutionStatus.SKIPPED -> "已跳过"; ExecutionStatus.DRAFT -> "进行中"; else -> "待完成" }, style = MaterialTheme.typography.labelLarge)
            if (onEdit != null || onDelete != null) Box {
                IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreVert, "项目操作") }
                DropdownMenu(menu, { menu = false }) {
                    if (onEdit != null) DropdownMenuItem(text = { Text("编辑计划目标") }, onClick = { menu = false; onEdit() })
                    if (onUp != null) DropdownMenuItem(text = { Text("上移项目") }, onClick = { menu = false; onUp() })
                    if (onDown != null) DropdownMenuItem(text = { Text("下移项目") }, onClick = { menu = false; onDown() })
                    if (onDelete != null) DropdownMenuItem(text = { Text("删除计划项目") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text("计划  ${item.target.summary(item.kind, unit)}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodySmall)
        if (record != null) {
            val actualLabel = if (record.status == ExecutionStatus.DRAFT && record.kindSnapshot == ExerciseKind.STRENGTH)
                "已确认 ${record.completedSets.size} / ${record.actual.sets.size} 组 · 草稿 ${record.actual.summary(record.kindSnapshot, unit)}"
                else "实际  ${record.actual.summary(record.kindSnapshot, unit)}"
            Text(actualLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            if (record.nameSnapshot != item.name) Text("记录时项目：${record.nameSnapshot}", style = MaterialTheme.typography.bodySmall)
            if (record.targetSnapshot != item.target) Text("记录时目标：${record.targetSnapshot.description(record.kindSnapshot, unit)}", style = MaterialTheme.typography.bodySmall)
            if (record.status == ExecutionStatus.SKIPPED) Text("跳过原因：${record.skipReason.ifBlank { "未填写" }}")
        }
        if (record == null && previous != null) Text("上次 ${previous.date.substring(5)}  ${previous.actual.summary(previous.kindSnapshot, unit)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (record == null || record.status == ExecutionStatus.DRAFT) FilledTonalButton(onClick = { quickComplete = true },
                enabled = !busy && date <= LocalDate.now().toString()) { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("完成") }
            TextButton(onClick = { if (onExecute != null) onExecute() else execute = true }, enabled = !busy && date <= LocalDate.now().toString(), modifier = Modifier.weight(1f)) {
                Text(if (date > LocalDate.now().toString()) "到训练当天记录" else if (record == null) "填写实际 / 跳过" else "查看 / 修改实际")
            }
        }
    }
    if (quickComplete) BatchCompletionDialog(data, date, vm, busy, setOf(item.id)) { quickComplete = false }
    if (execute) ExecutionForm(date, item, record, vm, unit, busy) { execute = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PlanScreen(data: FitnessData, date: String, onDate: (String) -> Unit, today: LocalDate, vm: FitnessViewModel, busy: Boolean, onStartSession: (String) -> Unit = {}) {
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
    var batchOpen by rememberSaveable(date) { mutableStateOf(false) }
    var planMenu by rememberSaveable(date) { mutableStateOf(false) }
    var monthOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var seedText by rememberSaveable { mutableStateOf<String?>(null) }
    fun reorder(id: String, delta: Int) = vm.change { current ->
        current.copy(plans = current.plans.map { p -> if (p.date != date) p else {
            val items = p.exercises.toMutableList(); val index = items.indexOfFirst { it.id == id }; val target = index + delta
            if (index >= 0 && target in items.indices) { val item = items.removeAt(index); items.add(target, item) }
            p.copy(exercises = items)
        } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDate(selected.minusWeeks(1).toString()) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${monday.year} 年 ${monday.monthValue} 月", style = MaterialTheme.typography.titleMedium)
                    Text("${monday.monthValue}/${monday.dayOfMonth} — ${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDate(selected.plusWeeks(1).toString()) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onDate(today.toString()) }) { Text("回到今天") }
                TextButton(onClick = { monthOpen = true }) { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp)); Text("月历") }
                Text("${data.plans.count { it.date >= monday.toString() && it.date <= monday.plusDays(6).toString() && it.exercises.isNotEmpty() }} 天已安排训练", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0L..6L).forEach { offset ->
                    val day = monday.plusDays(offset)
                    val status = data.dayStatus(day.toString())
                    val label = when (status) { DayStatus.EMPTY -> "·"; DayStatus.REST -> "休"; DayStatus.DONE -> "✓"; else -> "练" }
                    Surface(onClick = { onDate(day.toString()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        color = if (day == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (day == selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(listOf("一", "二", "三", "四", "五", "六", "日")[offset.toInt()], style = MaterialTheme.typography.labelSmall)
                            Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item { SectionCard {
            Text("$date · ${data.dayStatus(date).label}", color = MaterialTheme.colorScheme.primary)
            if (plan?.exercises?.isNotEmpty() == true) Text("完成 ${plan.exercises.count { data.execution(it.id)?.status == ExecutionStatus.DONE }} 项 · 跳过 ${plan.exercises.count { data.execution(it.id)?.status == ExecutionStatus.SKIPPED }} 项 · 共 ${plan.exercises.size} 项", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(plan?.name ?: "安排这一天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (plan != null && !plan.rest) IconButton(onClick = { rename = true }, enabled = !busy) { Icon(Icons.Outlined.Edit, "修改计划名称") }
                Box {
                    IconButton(onClick = { planMenu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreHoriz, "计划更多操作") }
                    DropdownMenu(planMenu, { planMenu = false }) {
                        DropdownMenuItem(text = { Text("复制本周") }, onClick = { planMenu = false; copyWeek = true })
                        DropdownMenuItem(text = { Text(if (plan?.rest == true) "取消休息日" else "设为休息日") }, onClick = { planMenu = false; restConfirm = true })
                        if (plan != null && !plan.rest) {
                            DropdownMenuItem(text = { Text("复制到其他日期") }, onClick = { planMenu = false; transfer = "copy" })
                            DropdownMenuItem(text = { Text("改期") }, onClick = { planMenu = false; transfer = "move" })
                            if (plan.exercises.isNotEmpty()) DropdownMenuItem(text = { Text("另存为训练模板") }, onClick = { planMenu = false; templateSave = true })
                        }
                    }
                }
            }
            if (plan?.rest == true) Text("已安排休息。添加训练或应用模板会将这天改为训练日。")
            if (plan == null) Text("从一个动作开始，也可以复用自己的训练模板。")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickerOpen = true }, enabled = !busy) { Text("＋ 添加项目") }
                OutlinedButton(onClick = { templatesOpen = true }, enabled = !busy) { Text("训练模板") }
            }
            if (plan?.exercises?.isNotEmpty() == true && date <= today.toString()) FilledTonalButton(onClick = { batchOpen = true },
                enabled = !busy && TrainingCompletion.eligible(data, date).isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("批量完成")
            }
            if (plan?.exercises?.isNotEmpty() == true && date <= today.toString()) Button(onClick = { onStartSession(date) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PlayArrow, null); Text("进入训练模式")
            }
        } }
        plan?.exercises.orEmpty().forEachIndexed { index, exercise -> item(key = exercise.id) {
            WorkoutCard(exercise, date, data, vm, busy,
                onEdit = { editId = exercise.id }, onDelete = { deleteId = exercise.id },
                onUp = if (index > 0) ({ reorder(exercise.id, -1) }) else null,
                onDown = if (index < plan!!.exercises.lastIndex) ({ reorder(exercise.id, 1) }) else null)
        } }
    }
    val editing = plan?.exercises?.find { it.id == editId }
    if (monthOpen) MonthCalendar(data, selected, { monthOpen = false }, onDate)
    if (pickerOpen) ExercisePicker(data, busy, { pickerOpen = false }) { seed -> seedText = seed?.let { fitnessJson.encodeToString(it) }; pickerOpen = false; addOpen = true }
    if (batchOpen) BatchCompletionDialog(data, date, vm, busy, onDismiss = { batchOpen = false })
    if (addOpen || editing != null) ExerciseForm(editing, data.settings.loadUnit, busy, onDismiss = { addOpen = false; editId = null }, seed = if (editing == null) seedText?.let { fitnessJson.decodeFromString<Exercise>(it) } else null) { item ->
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
