package com.xieqinglin.fitness.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

@Composable fun RestTimerCard(vm: FitnessViewModel, compact: Boolean = false) {
    val timer by vm.timer.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer) {
        elapsed = SystemClock.elapsedRealtime()
        while (timer.running && timer.remaining(elapsed) > 0) { delay(250); elapsed = SystemClock.elapsedRealtime() }
        if (timer.running && timer.remaining(elapsed) == 0L) vm.finishRest()
    }
    val seconds = (timer.remaining(elapsed) + 999) / 1000
    if (compact) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (timer.running) "组间休息" else "计时已暂停", style = MaterialTheme.typography.labelSmall)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = { vm.adjustRest(-15) }) { Text("−15", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = { if (timer.running) vm.pauseRest() else vm.resumeRest() }) { Icon(if (timer.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (timer.running) "暂停休息计时" else "继续休息计时") }
            IconButton(onClick = { vm.adjustRest(15) }) { Text("+15", style = MaterialTheme.typography.labelLarge) }
            IconButton(onClick = vm::cancelRest) { Icon(Icons.Outlined.Close, "结束休息") }
        }
        return
    }
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("组间休息", style = MaterialTheme.typography.labelMedium)
                Text("%02d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = { vm.adjustRest(-15) }, enabled = seconds > 0) { Text("−15秒") }
            TextButton(onClick = { if (seconds == 0L) vm.startRest(15) else vm.adjustRest(15) }) { Text("+15秒") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (seconds == 0L) {
                listOf(30, 60, 90).forEach { count -> OutlinedButton(onClick = { vm.startRest(count) }) { Text("${count}秒") } }
            } else {
                Button(onClick = { if (timer.running) vm.pauseRest() else vm.resumeRest() }) { Text(if (timer.running) "暂停" else "继续计时") }
                TextButton(onClick = vm::cancelRest) { Text("结束休息") }
            }
        }
    }
}
