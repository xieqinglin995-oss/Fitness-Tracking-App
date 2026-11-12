package com.xieqinglin.fitness.domain

import java.time.LocalDate
import kotlin.math.roundToLong

fun parseDuration(text: String): Double? {
    if (text.isBlank()) return null
    val parts = text.split(":")
    val minutes = parts[0].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
    if (parts.size == 1) return minutes * 60
    if (parts.size != 2 || minutes % 1 != 0.0) return null
    val seconds = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 60 } ?: return null
    return minutes * 60 + seconds
}

fun durationInput(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    val remainder = seconds - minutes * 60
    return if (remainder == 0.0) minutes.toString() else "$minutes:${formatNumber(remainder)}"
}
fun durationLabel(seconds: Double): String {
    val minutes = (seconds / 60).toLong()
    val remainder = seconds - minutes * 60
    return if (remainder == 0.0) "$minutes 分钟" else "$minutes 分 ${formatNumber(remainder)} 秒"
}
fun clockLabel(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
fun Values.pace(): String? {
    val distance = kilometers?.takeIf { it.isFinite() && it > 0 } ?: return null
    val time = seconds?.takeIf { it.isFinite() && it > 0 } ?: return null
    val perKm = (time / distance).roundToLong()
    return "%d:%02d /km".format(perKm / 60, perKm % 60)
}

data class ActivityClock(val exerciseId: String = "", val accrued: Long = 0, val started: Long = 0, val target: Long = 0) {
    val running get() = started > 0
    fun elapsed(now: Long): Long {
        val value = accrued + if (running) (now - started).coerceAtLeast(0) else 0
        return if (target > 0) value.coerceAtMost(target) else value
    }
    fun pause(now: Long) = copy(accrued = elapsed(now), started = 0)
    fun resume(now: Long) = copy(started = now)
}

data class TrainingReview(val days: Int, val strengthDays: Int, val cardioDays: Int, val sets: Int, val kilometers: Double, val seconds: Double)
fun Execution.hasConfirmedActivity() = status != ExecutionStatus.SKIPPED &&
    if (kindSnapshot == ExerciseKind.STRENGTH) completedSets.isNotEmpty() else status == ExecutionStatus.DONE && ((actual.seconds ?: 0.0) > 0 || (actual.kilometers ?: 0.0) > 0)
fun FitnessData.review(from: LocalDate, to: LocalDate): TrainingReview {
    val actual = executions.filter { it.status != ExecutionStatus.SKIPPED && it.date in from.toString()..to.toString() }
    val strength = actual.filter { it.kindSnapshot == ExerciseKind.STRENGTH && it.completedSets.isNotEmpty() }
    val cardio = actual.filter { it.kindSnapshot != ExerciseKind.STRENGTH && it.hasConfirmedActivity() }
    return TrainingReview((strength + cardio).map { it.date }.distinct().size, strength.map { it.date }.distinct().size,
        cardio.map { it.date }.distinct().size, strength.sumOf { it.completedSets.size }, cardio.sumOf { it.actual.kilometers ?: 0.0 }, cardio.sumOf { it.actual.seconds ?: 0.0 })
}

object RepeatingPlans {
    fun updateFuture(data: FitnessData, batch: String, template: WorkoutTemplate, selected: Set<String>, today: LocalDate = LocalDate.now()): FitnessData {
        require(batch.isNotBlank() && selected.isNotEmpty()) { "请选择要更新的日期" }
        require(selected.all { day -> day >= today.toString() && data.settings.sources.any { it.batchId == batch && it.date == day } && data.plan(day) != null }) { "只能更新本批次未来计划" }
        return selected.sorted().fold(data) { result, date ->
            require(result.plan(date)!!.exercises.none { result.execution(it.id) != null }) { "$date 已有实际记录" }
            val next = PlanOperations.apply(result, date, template.exercises, template.name, ConflictChoice.REPLACE)
            next.copy(settings = next.settings.copy(sources = next.settings.sources.map { if (it.date == date) it.copy(templateId = template.id) else it }))
        }
    }
    fun dates(start: LocalDate, end: LocalDate, weekdays: Set<Int>): List<LocalDate> {
        require(end >= start && end <= start.plusDays(83)) { "请选择不超过 12 周的起止日期" }
        require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) { "请至少选择一个星期" }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.filter { it.dayOfWeek.value in weekdays }.toList()
    }
    fun apply(data: FitnessData, template: WorkoutTemplate, dates: List<LocalDate>, choices: Map<String, ConflictChoice>, batch: String): FitnessData {
        require(dates.distinct().size == dates.size && dates.size <= 84 && batch.isNotBlank()) { "排期不合法" }
        return dates.fold(data) { result, day ->
            val date = day.toString()
            if (result.settings.sources.any { it.date == date && it.batchId == batch }) result
            else {
                val existing = result.plan(date)
                val choice = choices[date] ?: ConflictChoice.SKIP
                if (existing != null && choice == ConflictChoice.SKIP) result
                else {
                    require(existing?.exercises.orEmpty().none { result.execution(it.id) != null }) { "$date 已有实际记录，不能替换" }
                    val next = PlanOperations.apply(result, date, template.exercises, template.name, choice)
                    next.copy(settings = next.settings.copy(sources = next.settings.sources.filterNot { it.date == date } + PlanSource(date, template.id, batch)))
                }
            }
        }
    }
}
