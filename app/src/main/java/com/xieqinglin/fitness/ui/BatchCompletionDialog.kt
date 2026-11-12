package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel

@Composable fun BatchCompletionDialog(data: FitnessData, date: String, vm: FitnessViewModel, busy: Boolean,
    initialIds: Set<String>? = null, onDismiss: () -> Unit) {
    val eligible = TrainingCompletion.eligible(data, date)
    var selected by rememberSaveable(date) { mutableStateOf((initialIds ?: eligible.map { it.id }.toSet()).toList()) }
    val valid = selected.toSet().intersect(eligible.map { it.id }.toSet())
    FormDialog("批量完成", busy, onDismiss) {
        Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("未记录的项目按计划值完成；进行中的项目保留已填写的实际值。已完成和已跳过的项目不会被改动。",
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("已选 ${valid.size} / ${eligible.size} 项", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { selected = if (valid.size == eligible.size) emptyList() else eligible.map { it.id } }, enabled = !busy) {
                Text(if (valid.size == eligible.size) "取消全选" else "全选")
            }
        }
        eligible.forEach { item ->
            val actual = data.execution(item.id)
            Row(Modifier.fillMaxWidth().toggleable(item.id in valid, enabled = !busy, role = Role.Checkbox) {
                selected = if (it) selected + item.id else selected - item.id
            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.id in valid, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text((if (actual == null) "使用计划：" else "保留实际：") +
                        (actual?.actual ?: item.target).description(item.kind, data.settings.loadUnit),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (eligible.isEmpty()) Text("当天没有待完成的项目。")
        Button(onClick = { vm.completeTraining(date, valid, onDismiss) }, enabled = !busy && valid.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("完成所选（${valid.size} 项）") }
        TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("取消") }
    }
}
