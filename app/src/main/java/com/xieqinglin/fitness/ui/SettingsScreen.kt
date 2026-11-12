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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.xieqinglin.fitness.R
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel

@Composable fun SettingsScreen(data: FitnessData, vm: FitnessViewModel, busy: Boolean, onExport: () -> Unit, onImport: () -> Unit) {
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    val unit = data.settings.unit
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionCard {
            Text("记录单位", style = MaterialTheme.typography.titleMedium)
            Text("体重", style = MaterialTheme.typography.bodyMedium)
            UnitSelector(unit, !busy) { choice ->
                vm.change { it.copy(settings = it.settings.copy(unit = choice)) }
            }
            Text("训练负重", style = MaterialTheme.typography.bodyMedium)
            UnitSelector(data.settings.loadUnit, !busy) { choice ->
                vm.change { it.copy(settings = it.settings.copy(loadUnit = choice)) }
            }
            Text("1 公斤 = 2 斤。体重与负重可分别设置，历史数值会自动换算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { SectionCard {
            Text("目标体重", style = MaterialTheme.typography.titleMedium)
            Text(data.settings.goalGrams?.let { "${formatWeight(it, unit)} ${unit.label}" } ?: "未设置")
            Text("目标只作为图表参考，增重、减重或维持都由你决定。", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { goalOpen = true }, enabled = !busy) { Text(if (data.settings.goalGrams == null) "设置目标" else "修改 / 取消目标") }
        } }
        item { SectionCard {
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
            Text(lastBackup?.let { time -> "最近导出：" + runCatching { OffsetDateTime.parse(time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrDefault(time) }
                ?: "暂无本机导出时间记录", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            Text("${data.weights.size} 条体重 · ${data.plans.size} 日计划\n${data.executions.size} 条实际记录 · ${data.templates.size} 个模板")
            Button(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("导出备份文件") }
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("从备份恢复") }
            Text("导出时请选择自己能找回的位置。恢复会完整替换本机数据，须先确认并保存现有数据备份。", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard {
            Text("本地存储", style = MaterialTheme.typography.titleMedium)
            Text("无需账号，核心功能离线可用，不自动上传健康记录。卸载或清除应用数据会删除所有本地记录，请先导出备份。")
            Text("${stringResource(R.string.app_name)} · $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
