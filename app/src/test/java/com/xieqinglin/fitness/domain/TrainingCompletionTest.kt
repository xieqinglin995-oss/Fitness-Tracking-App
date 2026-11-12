package com.xieqinglin.fitness.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingCompletionTest {
    private val date = "2026-01-01"
    private val today = LocalDate.parse("2026-01-02")
    private val items = (1..4).map { Exercise(name = "动作 $it", kind = ExerciseKind.STRENGTH, target = Values(sets = listOf(SetValue(10, 20000)))) }
    private fun record(index: Int, status: ExecutionStatus) = Execution(date = date, exerciseId = items[index].id,
        nameSnapshot = items[index].name, kindSnapshot = items[index].kind, targetSnapshot = items[index].target,
        actual = Values(sets = listOf(SetValue(7, 15000))), status = status, skipReason = if (status == ExecutionStatus.SKIPPED) "休息" else "")
    private val original = FitnessData(plans = listOf(DailyPlan(date = date, exercises = items)),
        executions = listOf(record(1, ExecutionStatus.DRAFT), record(2, ExecutionStatus.DONE), record(3, ExecutionStatus.SKIPPED)))

    @Test fun batchUsesPlanForNewAndPreservesDraftActualAndFinalizedRecords() {
        val result = TrainingCompletion.complete(original, date, items.map { it.id }.toSet(), today)
        assertEquals(2, result.undo.after.size)
        assertEquals(items[0].target, result.data.execution(items[0].id)!!.actual)
        assertEquals(original.execution(items[1].id)!!.actual, result.data.execution(items[1].id)!!.actual)
        assertEquals(ExecutionStatus.DONE, result.data.execution(items[1].id)!!.status)
        assertEquals(original.execution(items[2].id), result.data.execution(items[2].id))
        assertEquals(original.execution(items[3].id), result.data.execution(items[3].id))
        Rules.validate(result.data, today)
    }

    @Test fun selectionOnlyCompletesChosenItemsAndRepeatCannotOverwrite() {
        val result = TrainingCompletion.complete(original, date, setOf(items[0].id), today)
        assertEquals(original.execution(items[1].id), result.data.execution(items[1].id))
        assertTrue(runCatching { TrainingCompletion.complete(result.data, date, setOf(items[0].id), today) }.isFailure)
    }

    @Test fun undoRestoresDraftAndRemovesOnlyCreatedRecords() {
        val result = TrainingCompletion.complete(original, date, items.take(2).map { it.id }.toSet(), today)
        val afterUnrelated = result.data.copy(weights = listOf(WeightEntry(date = date, grams = 80000)))
        val undone = TrainingCompletion.undo(afterUnrelated, result.undo)
        assertEquals(original.executions.toSet(), undone.executions.toSet())
        assertEquals(afterUnrelated.weights, undone.weights)
        assertTrue(runCatching { TrainingCompletion.undo(undone, result.undo) }.isFailure)
    }

    @Test fun undoDoesNotClobberSubsequentEditsOrDeletes() {
        val result = TrainingCompletion.complete(original, date, setOf(items[0].id), today)
        val edited = result.data.copy(executions = result.data.executions.map {
            if (it.exerciseId == items[0].id) it.copy(actual = Values(sets = listOf(SetValue(12)))) else it
        })
        assertTrue(runCatching { TrainingCompletion.undo(edited, result.undo) }.isFailure)
        assertTrue(runCatching { TrainingCompletion.undo(original, result.undo) }.isFailure)
    }

    @Test fun futureDatesEmptySelectionsAndStaleIdsAreRejected() {
        assertTrue(runCatching { TrainingCompletion.complete(original, "2026-01-03", setOf(items[0].id), today) }.isFailure)
        assertTrue(runCatching { TrainingCompletion.complete(original, date, emptySet(), today) }.isFailure)
        assertTrue(runCatching { TrainingCompletion.complete(original, date, setOf("removed"), today) }.isFailure)
    }

    @Test fun previousValuesMatchNameAndKindExcludeSkippedAndCurrentDate() {
        val data = original.copy(plans = original.plans + DailyPlan(date = "2026-01-02", exercises = listOf(items[2].freshCopy())),
            executions = original.executions + record(2, ExecutionStatus.DONE).copy(id = newId(), date = "2026-01-02", exerciseId = "other"))
        assertEquals(date, data.previousExecution(items[2], "2026-01-02")!!.date)
        assertNull(data.previousExecution(items[3], "2026-01-02"))
        assertNull(data.previousExecution(items[2].copy(kind = ExerciseKind.DURATION), "2026-01-02"))
    }
}
