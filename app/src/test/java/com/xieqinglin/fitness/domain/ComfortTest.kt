package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class ComfortTest {
    @Test fun restClockPauseResumeAndAdjustUseElapsedTime() {
        val started = RestClock().start(60, 1000)
        assertEquals(50000L, started.remaining(11000))
        val paused = started.pause(11000)
        assertFalse(paused.running)
        assertEquals(50000L, paused.remaining(90000))
        val resumed = paused.resume(100000).adjust(-15, 100000)
        assertEquals(35000L, resumed.remaining(100000))
        assertEquals(0L, resumed.remaining(200000))
        assertEquals(0L, resumed.adjust(-1000, 100000).remaining(100000))
    }
    @Test fun reminderMovesToNextDayWhenTimePassedAndPreservesZone() {
        val now = ZonedDateTime.parse("2026-11-12T08:00:00+08:00[Asia/Shanghai]")
        assertEquals("2026-11-13T08:00+08:00[Asia/Shanghai]", nextReminder(now, 8, 0).toString())
        assertEquals(now.withHour(18).withMinute(30), nextReminder(now, 18, 30))
        assertTrue(runCatching { nextReminder(now, 25, 0) }.isFailure)
    }
    @Test fun reminderHandlesDaylightSavingGap() {
        val now = ZonedDateTime.parse("2026-03-08T01:45:00-05:00[America/New_York]")
        val next = nextReminder(now, 2, 30)
        assertTrue(next.isAfter(now))
        assertEquals(3, next.hour)
    }
    @Test fun weeklyOverviewDoesNotCountRestSkippedOrFutureDaysAsTraining() {
        val item = Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 600.0))
        val today = LocalDate.parse("2026-11-12")
        fun record(date: String, status: ExecutionStatus) = Execution(date = date, exerciseId = newId(), nameSnapshot = item.name,
            kindSnapshot = item.kind, targetSnapshot = item.target, actual = item.target, status = status)
        val data = FitnessData(weights = listOf(WeightEntry(date = "2026-11-10", grams = 80000), WeightEntry(date = "2026-11-01", grams = 81000)),
            plans = listOf(DailyPlan(date = "2026-11-10", exercises = listOf(item)), DailyPlan(date = "2026-11-13", exercises = listOf(item.freshCopy()))),
            executions = listOf(record("2026-11-10", ExecutionStatus.DONE), record("2026-11-11", ExecutionStatus.SKIPPED), record("2026-11-12", ExecutionStatus.DRAFT)))
        assertEquals(WeekOverview(1, 1, 1, 1), data.weekOverview(today))
    }
    @Test fun recentExercisesPreferLatestDefinitionWithoutMixingTypes() {
        val first = Exercise(name = "训练", kind = ExerciseKind.DURATION, target = Values(seconds = 60.0))
        val latest = first.freshCopy().copy(target = Values(seconds = 90.0))
        val data = FitnessData(plans = listOf(DailyPlan(date = "2026-11-01", exercises = listOf(first)), DailyPlan(date = "2026-11-02", exercises = listOf(latest))))
        assertEquals(listOf(latest), data.recentExercises())
    }
    @Test fun presetsHaveMatchingTypesAndValidEditableTargets() { ExercisePresets.all.forEach { Rules.exercise(it) } }
}
