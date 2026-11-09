package com.xieqinglin.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.xieqinglin.fitness.domain.*
import com.xieqinglin.fitness.presentation.FitnessViewModel
import java.time.LocalDate

@Composable fun TransferDialog(data: FitnessData, source: DailyPlan, move: Boolean, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var target by rememberSaveable { mutableStateOf(LocalDate.parse(source.date).plusDays(1).toString()) }
    var choiceName by rememberSaveable(target) { mutableStateOf(ConflictChoice.SKIP.name) }
    val existing = data.plan(target)
    val recorded = source.exercises.any { data.execution(it.id) != null }
    val choice = if (existing == null) ConflictChoice.REPLACE else ConflictChoice.valueOf(choiceName)
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    FormDialog(if (move) "训练改期" else "复制当天计划", busy, onDismiss) {
        Text("来源日期：${source.date} · ${source.exercises.size} 个项目")
        if (move && recorded) Text("已有实际记录：原日期历史完整保留，只将尚未完成的项目复制到新日期。")
        else Text(if (move) "原日期尚无实际记录，将整体移动计划。" else "只复制计划目标与顺序，不复制实际值、完成或跳过状态。")
        DateField(target, { target = it; error = null }, "目标日期")
        if (existing != null) {
            Text("目标日期已有：${data.dayStatus(target).label} · ${existing.exercises.size} 个项目。")
            val blockedRest = source.rest && existing.exercises.any { data.execution(it.id) != null }
            if (blockedRest) Text("目标日期已有实际记录，不能替换为休息日。")
            ConflictOptions(choice, { choiceName = it.name }, allowAppend = !source.rest, allowReplace = !blockedRest)
        }
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = {
                try {
                    Rules.date(target); require(target != source.date) { "请选择不同的目标日期" }
                    vm.change(if (move) "改期已处理" else "计划已复制", onDismiss) { current ->
                        if (move) PlanOperations.move(current, source.date, target, choice)
                        else if (source.rest && (current.plan(target) == null || choice != ConflictChoice.SKIP)) PlanOperations.rest(current, target, true)
                        else if (source.rest) current
                        else PlanOperations.apply(current, target, source.exercises, source.name, choice)
                    }
                } catch (e: IllegalArgumentException) { error = e.message }
            }, enabled = !busy) { Text("确认") }
        }
    }
}

@Composable fun CopyWeekDialog(data: FitnessData, sourceMonday: LocalDate, vm: FitnessViewModel, busy: Boolean, onDismiss: () -> Unit) {
    var targetInput by rememberSaveable { mutableStateOf(sourceMonday.plusWeeks(1).toString()) }
    val targetMonday = runCatching { weekStart(Rules.date(targetInput)) }.getOrNull()
    var replaceDates by rememberSaveable(targetInput) { mutableStateOf(listOf<String>()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val sources = (0L..6L).mapNotNull { offset -> data.plan(sourceMonday.plusDays(offset).toString())?.let { offset to it } }
    FormDialog("复制一周安排", busy, onDismiss) {
        Text("来源周：$sourceMonday — ${sourceMonday.plusDays(6)}")
        Text("未安排日期不复制；所有实际记录与状态都不复制。目标日期有冲突时默认跳过，请逐日选择。")
        DateField(targetInput, { targetInput = it; error = null }, "目标周任意一天")
        if (targetMonday != null) {
            Text("目标周：$targetMonday — ${targetMonday.plusDays(6)}", style = MaterialTheme.typography.titleSmall)
            sources.forEach { (offset, source) ->
                val target = targetMonday.plusDays(offset).toString()
                val existing = data.plan(target)
                HorizontalDivider()
                Text("$target ← ${source.date} · ${if (source.rest) "休息日" else "${source.exercises.size} 项训练"}")
                if (existing == null) Text("无冲突，将新建。", style = MaterialTheme.typography.bodySmall)
                else {
                    val blocked = source.rest && existing.exercises.any { data.execution(it.id) != null }
                    Text("已有 ${data.dayStatus(target).label} / ${existing.exercises.size} 项。${if (blocked) "已有实际记录，不能替换成休息日。" else "替换会保留含实际记录的项目。"}", style = MaterialTheme.typography.bodySmall)
                    ConflictOptions(if (target in replaceDates) ConflictChoice.REPLACE else ConflictChoice.SKIP,
                        { choice -> replaceDates = if (choice == ConflictChoice.REPLACE) replaceDates + target else replaceDates - target }, false, !blocked)
                }
            }
        }
        if (sources.isEmpty()) Text("来源周没有可复制的安排。")
        ErrorText(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            Button(onClick = {
                if (targetMonday == null || targetMonday == sourceMonday) error = "请选择与来源不同的有效目标周"
                else {
                    val choices = (0L..6L).associate { offset ->
                        val date = targetMonday.plusDays(offset).toString()
                        date to if (data.plan(date) == null || date in replaceDates) ConflictChoice.REPLACE else ConflictChoice.SKIP
                    }
                    vm.change("周安排已复制", onDismiss) { PlanOperations.copyWeek(it, sourceMonday, targetMonday, choices) }
                }
            }, enabled = !busy && sources.isNotEmpty()) { Text("按上述选择复制") }
        }
    }
}
