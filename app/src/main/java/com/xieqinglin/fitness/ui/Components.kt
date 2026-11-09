package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xieqinglin.fitness.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun displayDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE))

@Composable fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ConfirmDialog(title: String, text: String, confirm: String = "确认", busy: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}

@Composable fun FormDialog(title: String, busy: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 740.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
            }
        }
    }
}

@Composable fun ErrorText(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DateField(value: String, onChange: (String) -> Unit, label: String = "归属日期", allowFuture: Boolean = true) {
    var picker by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onChange, label = { Text("$label · YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
        TextButton(onClick = { picker = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text("选择") }
    }
    if (picker) {
        val parsed = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val state = rememberDatePickerState(initialSelectedDateMillis = parsed.toEpochDay() * 86400000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowFuture || utcTimeMillis / 86400000L <= LocalDate.now().toEpochDay()
            })
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / 86400000L).toString()) }; picker = false }) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text("取消") } }) { DatePicker(state) }
    }
}

@Composable fun Metric(label: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
