package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingToolsTest {
    @Test fun durationKeepsLegacyMinutesAndSecondPrecision() {
        assertEquals(1290.0, parseDuration("21:30")!!, 0.0)
        assertEquals(1290.0, parseDuration("21.5")!!, 0.0)
        assertEquals("1 分 30 秒", Values(seconds = 90.0).summary(ExerciseKind.DURATION, WeightUnit.KG))
        listOf(0.0, 90.0, 90.25, 1290.0, 86400.0).forEach { assertEquals(it, parseDuration(durationInput(it))!!, .001) }
        listOf("", "-1", "1:60", "1.5:20", "NaN", "2:3:4").forEach { assertNull(parseDuration(it)) }
    }
    @Test fun activityClockExcludesPausesAndCapsCountdown() {
        val paused = ActivityClock("run", started = 1000).pause(11000)
        assertEquals(10000L, paused.elapsed(21000))
        val resumed = paused.resume(21000)
        assertEquals(15000L, resumed.elapsed(26000))
        assertEquals(60000L, ActivityClock("stretch", started = 1000, target = 60000).elapsed(200000))
        assertEquals(0L, ActivityClock("run", started = 1000).elapsed(500))
    }
    @Test fun paceUsesOnlyRecordedCardioTimeAndDistance() {
        assertEquals("7:10 /km", Values(seconds = 1290.0, kilometers = 3.0).pace())
        assertNull(Values(seconds = 0.0, kilometers = 3.0).pace())
        assertNull(Values(seconds = 10.0, kilometers = 0.0).pace())
        assertNull(Values(kilometers = 3.0).pace())
    }
    @Test fun mixedTrainingDaysAreDeduplicatedAndPureDraftsIgnored() {
        val date = LocalDate.parse("2026-01-01")
        fun record(id: String, kind: ExerciseKind, status: ExecutionStatus, checks: List<Int> = emptyList()) =
            Execution(exerciseId = id, date = date.toString(), nameSnapshot = id, kindSnapshot = kind,
                targetSnapshot = Values(), actual = Values(sets = if (kind == ExerciseKind.STRENGTH) listOf(SetValue(10)) else emptyList(), seconds = if (kind != ExerciseKind.STRENGTH) 60.0 else null), status = status, completedSets = checks)
        val data = FitnessData(executions = listOf(record("s", ExerciseKind.STRENGTH, ExecutionStatus.DRAFT, listOf(0)),
            record("c", ExerciseKind.DURATION, ExecutionStatus.DONE), record("draft", ExerciseKind.DURATION, ExecutionStatus.DRAFT), record("skip", ExerciseKind.DURATION, ExecutionStatus.SKIPPED)))
        val summary = data.review(date, date)
        assertEquals(1, summary.days); assertEquals(1, summary.strengthDays); assertEquals(1, summary.cardioDays)
        assertEquals(1, summary.sets); assertEquals(1, data.weekOverview(date).trainedDays)
    }
    @Test fun scheduleIsBoundedIdempotentAndProtectsActuals() {
        val start = LocalDate.parse("2026-01-01")
        val days = RepeatingPlans.dates(start, start.plusDays(27), setOf(1, 3, 5))
        assertEquals(12, days.size)
        val exercise = Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 60.0))
        val template = WorkoutTemplate(name = "常用", exercises = listOf(exercise))
        val first = RepeatingPlans.apply(FitnessData(templates = listOf(template)), template, days, emptyMap(), "batch")
        val second = RepeatingPlans.apply(first, template, days, emptyMap(), "batch")
        assertEquals(first, second)
        assertEquals(12, first.plans.flatMap { it.exercises }.map { it.id }.distinct().size)
        val day = days.first().toString(); val item = first.plan(day)!!.exercises.first()
        val done = first.copy(executions = listOf(Execution(date = day, exerciseId = item.id, nameSnapshot = item.name, kindSnapshot = item.kind, targetSnapshot = item.target, actual = item.target, status = ExecutionStatus.DONE)))
        assertTrue(runCatching { RepeatingPlans.apply(done, template, listOf(days.first()), mapOf(day to ConflictChoice.REPLACE), "other") }.isFailure)
        assertEquals(done, RepeatingPlans.apply(done, template, listOf(days.first()), emptyMap(), "other"))
        assertTrue(runCatching { RepeatingPlans.dates(start, start.plusWeeks(13), setOf(1)) }.isFailure)
    }
    @Test fun sevenDayComparisonCountsCalendarWindowsBeforeSubtracting() {
        val today = LocalDate.parse("2026-01-14")
        val entries = listOf(0, 2, 6, 7, 9, 13).map { i -> WeightEntry(date = today.minusDays(i.toLong()).toString(), grams = if (i < 7) 80000 else 81000) }
        assertEquals(-1000.0, Statistics.comparison(entries, today).delta!!, 0.0)
        assertNull(Statistics.comparison(entries.dropLast(1), today).delta)
    }
    @Test fun updatingFutureBatchDoesNotRewritePastOrStartedRecords() {
        val start = LocalDate.parse("2026-01-01")
        val days = RepeatingPlans.dates(start, start.plusDays(27), setOf(1, 3, 5))
        val item = Exercise(name = "拉伸", kind = ExerciseKind.DURATION, target = Values(seconds = 60.0))
        val template = WorkoutTemplate(name = "常用", exercises = listOf(item))
        val original = RepeatingPlans.apply(FitnessData(), template, days, emptyMap(), "batch")
        val firstDate = days.first().toString()
        val first = original.plan(firstDate)!!.exercises.single()
        val record = Execution(date = firstDate, exerciseId = first.id, nameSnapshot = first.name, kindSnapshot = first.kind, targetSnapshot = first.target, actual = first.target, status = ExecutionStatus.DONE)
        val data = original.copy(executions = listOf(record))
        val updated = template.copy(exercises = listOf(item.copy(target = Values(seconds = 120.0))))
        val lastDate = days.last().toString()
        val result = RepeatingPlans.updateFuture(data, "batch", updated, setOf(lastDate), start.plusDays(14))
        assertEquals(record, result.executions.single())
        assertEquals(data.plan(firstDate), result.plan(firstDate))
        assertEquals(120.0, result.plan(lastDate)!!.exercises.single().target.seconds!!, 0.0)
        assertTrue(runCatching { RepeatingPlans.updateFuture(data, "batch", updated, setOf(firstDate), start) }.isFailure)
    }
}
