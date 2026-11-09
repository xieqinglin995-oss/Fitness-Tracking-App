package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel

@Composable fun SettingsScreen(data: FitnessData, vm: FitnessViewModel, busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    val unit = data.settings.unit
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard {
            Text("按自己的习惯记录", style = MaterialTheme.typography.titleLarge)
            Text("体重与训练负重单位")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeightUnit.entries.forEach { choice -> FilterChip(unit == choice, { vm.change { it.copy(settings = it.settings.copy(unit = choice)) } },
                    enabled = !busy, label = { Text(choice.label) }) }
            }
            Text("1 kg = 2 斤。切换只改变显示，不改变底层记录。", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard {
            Text("目标体重（可选）", style = MaterialTheme.typography.titleLarge)
            Text(data.settings.goalGrams?.let { "${formatWeight(it, unit)} ${unit.label}" } ?: "未设置")
            Text("目标只作为图表参考，增重、减重或维持都由你决定。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { goalOpen = true }, enabled = !busy) { Text(if (data.settings.goalGrams == null) "设置目标" else "修改 / 取消目标") }
        } }
        item { SectionCard {
            Text("备份与恢复", style = MaterialTheme.typography.titleLarge)
            Text("${data.weights.size} 条体重 · ${data.plans.size} 日计划\n${data.executions.size} 条实际记录 · ${data.templates.size} 个模板")
            Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("导出完整 JSON 备份") }
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("从备份恢复…") }
            Text("导出时请选择自己能找回的位置。恢复会完整替换本机数据，须先确认并保存现有数据备份。", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard {
            Text("数据只在你的设备上", style = MaterialTheme.typography.titleLarge)
            Text("无需账号，核心功能离线可用，不自动上传健康记录。卸载或清除应用数据会删除所有本地记录，请先导出备份。")
            Text("日日健身 · 1.0.0\n记录属于日期，不随时区改变。", style = MaterialTheme.typography.bodySmall)
        } }
    }
    if (goalOpen) {
        var number by rememberSaveable { mutableStateOf(data.settings.goalGrams?.let { formatWeight(it, unit) } ?: "") }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        FormDialog("目标体重", busy, { goalOpen = false }) {
            OutlinedTextField(number, { number = it; error = null }, label = { Text("目标 · ${unit.label}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            ErrorText(error)
            if (data.settings.goalGrams != null) TextButton(onClick = { vm.change("目标已取消", { goalOpen = false }) { it.copy(settings = it.settings.copy(goalGrams = null)) } }, enabled = !busy) { Text("取消目标体重") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { goalOpen = false }, enabled = !busy) { Text("返回") }
                Button(onClick = {
                    try {
                        val grams = Rules.weight(number, unit)
                        vm.change(onSuccess = { goalOpen = false }) { it.copy(settings = it.settings.copy(goalGrams = grams)) }
                    } catch (e: IllegalArgumentException) { error = e.message }
                }, enabled = !busy) { Text("保存") }
            }
        }
    }
}
