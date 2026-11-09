package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlanOperationsTest {
    private val item = Exercise(name = "深蹲", kind = ExerciseKind.STRENGTH, target = Values(sets = listOf(SetValue(10))))
    private val plan = DailyPlan(date = "2026-11-02", exercises = listOf(item))
    private fun actual(status: ExecutionStatus) = Execution(date = plan.date, exerciseId = item.id, nameSnapshot = item.name,
        kindSnapshot = item.kind, targetSnapshot = item.target, actual = Values(sets = listOf(SetValue(12))), status = status)

    @Test fun allSkippedIsEndedWithNoCompletions() {
        val data = FitnessData(plans = listOf(plan), executions = listOf(actual(ExecutionStatus.SKIPPED)))
        assertEquals(DayStatus.ENDED, data.dayStatus(plan.date))
        assertEquals(0, data.executions.count { it.status == ExecutionStatus.DONE })
    }
    @Test fun draftAndMixedStatusesAreDistinct() {
        val second = item.freshCopy()
        val data = FitnessData(plans = listOf(plan.copy(exercises = listOf(item, second))), executions = listOf(actual(ExecutionStatus.DONE)))
        assertEquals(DayStatus.ACTIVE, data.dayStatus(plan.date))
        val skipped = actual(ExecutionStatus.SKIPPED).copy(id = newId(), exerciseId = second.id)
        assertEquals(DayStatus.ENDED, data.copy(executions = data.executions + skipped).dayStatus(plan.date))
        assertEquals(DayStatus.DONE, data.copy(plans = listOf(plan)).dayStatus(plan.date))
    }
    @Test fun restAndUnplannedAreDifferent() {
        assertEquals(DayStatus.EMPTY, FitnessData().dayStatus(plan.date))
        assertEquals(DayStatus.REST, PlanOperations.rest(FitnessData(), plan.date, true).dayStatus(plan.date))
    }
    @Test fun templateApplicationMakesIndependentCopies() {
        val template = WorkoutTemplate(name = "力量", exercises = listOf(item))
        val result = PlanOperations.apply(FitnessData(templates = listOf(template)), plan.date, template.exercises, template.name, ConflictChoice.REPLACE)
        assertNotEquals(item.id, result.plans.single().exercises.single().id)
        val afterDelete = result.copy(templates = emptyList())
        assertEquals(item.target, afterDelete.plans.single().exercises.single().target)
    }
    @Test fun replacingPlanRetainsActualRecordsAndParentItems() {
        val data = FitnessData(plans = listOf(plan), executions = listOf(actual(ExecutionStatus.DONE)))
        val result = PlanOperations.apply(data, plan.date, listOf(item), "新计划", ConflictChoice.REPLACE)
        assertEquals(2, result.plans.single().exercises.size)
        assertEquals(data.executions, result.executions)
        assertTrue(result.plans.single().exercises.contains(item))
    }
    @Test fun restCannotEraseHistory() {
        val data = FitnessData(plans = listOf(plan), executions = listOf(actual(ExecutionStatus.DONE)))
        assertTrue(runCatching { PlanOperations.rest(data, plan.date, true) }.isFailure)
    }
    @Test fun copyWeekNeverCopiesExecution() {
        val data = FitnessData(plans = listOf(plan), executions = listOf(actual(ExecutionStatus.DONE)))
        val target = LocalDate.parse("2026-11-09")
        val result = PlanOperations.copyWeek(data, LocalDate.parse(plan.date), target, mapOf(target.toString() to ConflictChoice.REPLACE))
        assertEquals(1, result.executions.size)
        assertEquals(DayStatus.PENDING, result.dayStatus(target.toString()))
        assertEquals(item.target, result.plan(target.toString())!!.exercises.single().target)
    }
    @Test fun conflictsSkipWithoutAnyChanges() {
        val data = FitnessData(plans = listOf(plan))
        assertEquals(data, PlanOperations.apply(data, plan.date, listOf(item), "新计划", ConflictChoice.SKIP))
    }
    @Test fun moveWithActualOnlyCopiesPendingAndRetainsOriginal() {
        val second = item.freshCopy()
        val data = FitnessData(plans = listOf(plan.copy(exercises = listOf(item, second))), executions = listOf(actual(ExecutionStatus.DONE)))
        val result = PlanOperations.move(data, plan.date, "2026-11-03", ConflictChoice.REPLACE)
        assertEquals(data.plan(plan.date), result.plan(plan.date))
        assertEquals(1, result.plan("2026-11-03")!!.exercises.size)
        assertNotEquals(second.id, result.plan("2026-11-03")!!.exercises.single().id)
        assertEquals(data.executions, result.executions)
    }
    @Test fun moveWithoutActualRemovesSourceOnlyAfterSuccessfulCopy() {
        val result = PlanOperations.move(FitnessData(plans = listOf(plan)), plan.date, "2026-11-03", ConflictChoice.REPLACE)
        assertNull(result.plan(plan.date))
        assertNotNull(result.plan("2026-11-03"))
    }
}
