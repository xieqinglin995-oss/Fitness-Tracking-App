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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.Alignment
import com.xieqinglin.fitness.platform.ThemeChoice
import com.xieqinglin.fitness.platform.Reminders
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(data: FitnessData, vm: FitnessViewModel, busy: Boolean, onExport: () -> Unit, onImport: () -> Unit, onCsv: (Boolean) -> Unit = {}) {
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    val unit = data.settings.unit
    val lastBackup by vm.lastBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val options by vm.comfort.collectAsStateWithLifecycle()
    val recoveries by vm.recoveries.collectAsStateWithLifecycle()
    var recoveryOpen by rememberSaveable { mutableStateOf(false) }
    var reminderTime by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReminder by rememberSaveable { mutableStateOf<String?>(null) }
    fun enable(type: String, checked: Boolean) {
        vm.setComfort { if (type == "weight") it.copy(weightReminder = checked) else it.copy(trainingReminder = checked) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingReminder?.let { if (granted && Reminders.allowed(context)) enable(it, true) else vm.inform("通知未开启，记录功能不受影响") }
        pendingReminder = null
    }
    fun toggle(type: String, checked: Boolean) {
        if (checked && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = type; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (checked && !Reminders.allowed(context)) vm.inform("请先在系统通知设置中允许通知")
        else enable(type, checked)
    }
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
            Text("外观与训练", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeChoice.entries.forEach { choice -> FilterChip(options.theme == choice, { vm.setComfort { it.copy(theme = choice) } }, label = { Text(choice.label) }) }
            }
            SettingSwitch("训练时保持亮屏", "仅在训练模式内生效", options.keepAwake) { checked -> vm.setComfort { it.copy(keepAwake = checked) } }
            SettingSwitch("轻触反馈", "确认训练组时跟随系统震动设置", options.haptics) { checked -> vm.setComfort { it.copy(haptics = checked) } }
            Text("默认组间休息", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 30, 60, 90, 120).forEach { seconds -> FilterChip(options.restSeconds == seconds, { vm.setComfort { it.copy(restSeconds = seconds) } }, label = { Text(if (seconds == 0) "关闭自动" else "${seconds}秒") }) }
            }
        } }
        item { SectionCard {
            Text("按自己的节奏提醒", style = MaterialTheme.typography.titleMedium)
            SettingSwitch("称重提醒", "%02d:%02d · 今天已记录就不再提醒".format(options.weightHour, options.weightMinute), options.weightReminder) { toggle("weight", it) }
            TextButton(onClick = { reminderTime = "weight" }) { Text("修改称重时间") }
            SettingSwitch("训练提醒", "%02d:%02d · 仅当天有待完成项目时提醒".format(options.trainingHour, options.trainingMinute), options.trainingReminder) { toggle("training", it) }
            TextButton(onClick = { reminderTime = "training" }) { Text("修改训练时间") }
            Text("提醒默认关闭。启用时才申请通知权限，系统省电可能使提醒略有延迟。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("系统通知设置") }
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onCsv(false) }, enabled = !busy) { Text("导出体重表格") }
                TextButton(onClick = { onCsv(true) }, enabled = !busy) { Text("导出训练表格") }
            }
            HorizontalDivider()
            Text("本机恢复点", style = MaterialTheme.typography.titleSmall)
            Text("修改前保留恢复点，最多每分钟一个、保留最近 8 个。卸载会一同删除，换机仍需导出备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { recoveryOpen = true }, enabled = !busy && recoveries.isNotEmpty()) { Text("查看恢复点（${recoveries.size}）") }
            Text("导出时请选择自己能找回的位置。恢复会完整替换本机数据，须先确认并保存现有数据备份。", style = MaterialTheme.typography.bodySmall)
        } }
        item { SectionCard {
            Text("本地存储", style = MaterialTheme.typography.titleMedium)
            Text("无需账号，核心功能离线可用，不自动上传健康记录。卸载或清除应用数据会删除所有本地记录，请先导出备份。")
            Text("${stringResource(R.string.app_name)} · $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
    if (reminderTime != null) {
        val type = reminderTime!!
        val time = rememberTimePickerState(if (type == "weight") options.weightHour else options.trainingHour,
            if (type == "weight") options.weightMinute else options.trainingMinute, is24Hour = true)
        AlertDialog(onDismissRequest = { reminderTime = null }, title = { Text(if (type == "weight") "称重时间" else "训练时间") },
            text = { TimeInput(time) },
            confirmButton = { TextButton(onClick = {
                vm.setComfort { if (type == "weight") it.copy(weightHour = time.hour, weightMinute = time.minute) else it.copy(trainingHour = time.hour, trainingMinute = time.minute) }
                reminderTime = null
            }) { Text("保存时间") } }, dismissButton = { TextButton(onClick = { reminderTime = null }) { Text("取消") } })
    }
    if (recoveryOpen) FormDialog("本机恢复点", busy, { recoveryOpen = false }) {
        Text("恢复前会显示范围，并要求先导出当前数据。", style = MaterialTheme.typography.bodySmall)
        recoveries.forEach { point ->
            OutlinedButton(onClick = { recoveryOpen = false; vm.inspectRecovery(point.name) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(Instant.ofEpochMilli(point.modified).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")))
            }
        }
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

@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
