package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.serialization.encodeToString

@Composable fun TemplateManager(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var newTemplate by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var applyId by rememberSaveable { mutableStateOf<String?>(null) }
    FormDialog("训练模板", busy, onDismiss) {
        Text("模板只保存目标。应用后生成独立副本，后续修改模板不会影响日期计划。")
        Button(onClick = { newTemplate = true }, enabled = !busy) { Text("＋ 新建模板") }
        if (data.templates.isEmpty()) EmptyState("还没有模板", "新建常用组合，或将某天计划另存为模板。")
        data.templates.forEach { template ->
            HorizontalDivider()
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text("${template.exercises.size} 个项目 · ${template.exercises.joinToString("、") { it.name }}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { editorId = template.id }, enabled = !busy) { Text("编辑 / 重命名") }
                TextButton(onClick = { deleteId = template.id }, enabled = !busy) { Text("删除") }
                FilledTonalButton(onClick = { applyId = template.id }, enabled = !busy) { Text("应用") }
            }
        }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") }
    }
    val editing = data.templates.find { it.id == editorId }
    if (newTemplate || editing != null) TemplateEditor(editing, data.settings.loadUnit, busy, { newTemplate = false; editorId = null }) { template ->
        vm.change("模板已保存", { newTemplate = false; editorId = null }) { it.copy(templates = it.templates.filterNot { t -> t.id == template.id } + template) }
    }
    deleteId?.let { id -> ConfirmDialog("删除模板？", "已应用到日期的独立计划与历史实际记录都会保留。", "删除", busy,
        { deleteId = null }, { vm.change("模板已删除", { deleteId = null }) { it.copy(templates = it.templates.filterNot { t -> t.id == id }) } }) }
    val applying = data.templates.find { it.id == applyId }
    if (applying != null) {
        var choice by rememberSaveable(applying.id) { mutableStateOf(ConflictChoice.APPEND.name) }
        val target = data.plan(date)
        FormDialog("应用到 $date", busy, { applyId = null }) {
            Text("${applying.name} · ${applying.exercises.size} 个项目")
            if (target != null) {
                Text("目标日期为 ${data.dayStatus(date).label}，已有 ${target.exercises.size} 个项目。替换时，所有含实际记录的项目仍保留。")
                ConflictOptions(ConflictChoice.valueOf(choice), { choice = it.name }, allowAppend = true)
            } else Text("将在这个日期建立独立的训练计划。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { applyId = null }, enabled = !busy) { Text("取消") }
                Button(onClick = { vm.change("模板已应用", { applyId = null; onDismiss() }) {
                    val next = PlanOperations.apply(it, date, applying.exercises, applying.name, if (target == null) ConflictChoice.REPLACE else ConflictChoice.valueOf(choice))
                    next.copy(settings = next.settings.copy(sources = next.settings.sources.filterNot { s -> s.date == date } + PlanSource(date, applying.id)))
                } }, enabled = !busy) { Text("确认应用") }
            }
        }
    }
}

@Composable private fun TemplateEditor(original: WorkoutTemplate?, unit: WeightUnit, busy: Boolean, onDismiss: () -> Unit, onSave: (WorkoutTemplate) -> Unit) {
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var itemsText by rememberSaveable { mutableStateOf(fitnessJson.encodeToString(original?.exercises ?: emptyList<Exercise>())) }
    val items = fitnessJson.decodeFromString<List<Exercise>>(itemsText)
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    FormDialog(if (original == null) "新建训练模板" else "编辑训练模板", busy, onDismiss) {
        OutlinedTextField(name, { name = it }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth())
        items.forEachIndexed { index, exercise ->
            HorizontalDivider()
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(exercise.target.description(exercise.kind, unit), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { editingId = exercise.id }) { Text("编辑") }
                TextButton(onClick = { itemsText = fitnessJson.encodeToString(items.filterNot { it.id == exercise.id }) }) { Text("移除") }
                if (index > 0) TextButton(onClick = {
                    val reordered = items.toMutableList(); reordered.add(index - 1, reordered.removeAt(index)); itemsText = fitnessJson.encodeToString(reordered)
                }) { Text("上移") }
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text("＋ 添加项目") }
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = {
                if (name.trim().length !in 1..100 || items.isEmpty()) error = "请填写 1–100 字模板名称，并添加至少一个项目"
                else onSave(WorkoutTemplate(original?.id ?: newId(), name.trim(), items))
            }, enabled = !busy) { Text("保存模板") }
        }
    }
    if (adding || editingId != null) ExerciseForm(items.find { it.id == editingId }, unit, false, onDismiss = { adding = false; editingId = null }) { exercise ->
        itemsText = fitnessJson.encodeToString(if (items.any { it.id == exercise.id }) items.map { if (it.id == exercise.id) exercise else it } else items + exercise)
        adding = false; editingId = null
    }
}

@Composable fun ConflictOptions(choice: ConflictChoice, onChoice: (ConflictChoice) -> Unit, allowAppend: Boolean, allowReplace: Boolean = true) {
    Column {
        if (allowAppend) FilterChip(choice == ConflictChoice.APPEND, { onChoice(ConflictChoice.APPEND) }, label = { Text("追加项目") })
        FilterChip(choice == ConflictChoice.REPLACE, { onChoice(ConflictChoice.REPLACE) }, enabled = allowReplace, label = { Text("替换（保留含实际记录的项目）") })
        FilterChip(choice == ConflictChoice.SKIP, { onChoice(ConflictChoice.SKIP) }, label = { Text("跳过 / 不修改") })
    }
}
