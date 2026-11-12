package com.xieqinglin.fitness.domain

import java.time.LocalDate
import java.time.ZonedDateTime

data class WeekOverview(val recordedDays: Int, val trainedDays: Int, val completedItems: Int, val plannedItems: Int)

fun FitnessData.weekOverview(today: LocalDate): WeekOverview {
    val start = weekStart(today).toString()
    val end = today.toString()
    return WeekOverview(weights.count { it.date in start..end },
        executions.filter { it.date in start..end && it.status == ExecutionStatus.DONE }.map { it.date }.distinct().size,
        executions.count { it.date in start..end && it.status == ExecutionStatus.DONE },
        plans.filter { it.date in start..end }.sumOf { it.exercises.size })
}

fun FitnessData.recentExercises(): List<Exercise> = plans.sortedByDescending { it.date }.flatMap { it.exercises }
    .distinctBy { it.name.trim().lowercase() to it.kind }.take(20)

fun nextReminder(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    require(hour in 0..23 && minute in 0..59)
    val candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
}

data class RestClock(val deadline: Long = 0, val pausedMillis: Long = 0) {
    val running get() = deadline > 0
    fun remaining(elapsed: Long) = if (running) (deadline - elapsed).coerceAtLeast(0) else pausedMillis
    fun start(seconds: Int, elapsed: Long) = RestClock(elapsed + seconds.coerceIn(1, 3600) * 1000L)
    fun pause(elapsed: Long) = RestClock(pausedMillis = remaining(elapsed))
    fun resume(elapsed: Long) = if (pausedMillis > 0) RestClock(deadline = elapsed + pausedMillis) else this
    fun adjust(seconds: Int, elapsed: Long): RestClock {
        val next = (remaining(elapsed) + seconds * 1000L).coerceIn(0, 3_600_000)
        return if (running && next > 0) RestClock(deadline = elapsed + next) else RestClock(pausedMillis = next)
    }
}

object ExercisePresets {
    val all = listOf(
        strength("深蹲", 12), strength("俯卧撑", 10), strength("哑铃划船", 12), strength("卧推", 10), strength("弓步蹲", 10),
        Exercise(name = "平板支撑", kind = ExerciseKind.DURATION, target = Values(seconds = 60.0)),
        Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0)),
        Exercise(name = "跳绳", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0)),
        Exercise(name = "快走", kind = ExerciseKind.DISTANCE, target = Values(kilometers = 3.0)),
        Exercise(name = "跑步", kind = ExerciseKind.DISTANCE, target = Values(kilometers = 3.0)),
        Exercise(name = "骑行", kind = ExerciseKind.DISTANCE, target = Values(kilometers = 5.0)),
    )
    private fun strength(name: String, reps: Int) = Exercise(name = name, kind = ExerciseKind.STRENGTH, target = Values(sets = List(3) { SetValue(reps) }))
}
