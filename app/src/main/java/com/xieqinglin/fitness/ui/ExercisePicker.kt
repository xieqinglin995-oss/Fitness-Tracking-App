package com.xieqinglin.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*

@Composable fun ExercisePicker(data: FitnessData, busy: Boolean, onDismiss: () -> Unit, onPick: (Exercise?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val recent = data.recentExercises()
    val source = (recent + ExercisePresets.all).distinctBy { it.name.trim().lowercase() to it.kind }
    FormDialog("添加训练", busy, onDismiss) {
        OutlinedTextField(query, { query = it }, label = { Text("搜索动作或项目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("最近使用优先。选择后可调整组次、时长或距离。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onPick(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("自定义项目") }
        source.filter { it.name.contains(query.trim(), ignoreCase = true) }.forEach { item ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onPick(item) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.target.summary(item.kind, data.settings.loadUnit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (recent.any { it.name == item.name && it.kind == item.kind }) "最近" else "常用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("以上是可编辑的记录模板，请按自己的训练习惯调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
