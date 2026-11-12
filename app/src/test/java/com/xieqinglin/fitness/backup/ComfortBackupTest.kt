package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ComfortBackupTest {
    @get:Rule val folder = TemporaryFolder()
    private val day = "2026-01-01"
    private val item = Exercise(name = "深蹲", kind = ExerciseKind.STRENGTH, target = Values(sets = listOf(SetValue(10), SetValue(10))))
    private fun data(status: ExecutionStatus = ExecutionStatus.DONE) = FitnessData(plans = listOf(DailyPlan(date = day, exercises = listOf(item))),
        executions = listOf(Execution(date = day, exerciseId = item.id, nameSnapshot = item.name, kindSnapshot = item.kind,
            targetSnapshot = item.target, actual = item.target, status = status)))

    @Test fun v2CompletedWorkoutMigratesAllSetsAndV3KeepsPartialProgress() {
        val legacy = BackupCodec.encode(data()).replace("\"schemaVersion\":3", "\"schemaVersion\":2").replace(",\"completedSets\":[0,1]", "")
        assertEquals(listOf(0, 1), BackupCodec.decode(legacy).data.executions.single().completedSets)
        val partial = data(ExecutionStatus.DRAFT).let { it.copy(executions = listOf(it.executions.single().copy(completedSets = listOf(0)))) }
        assertEquals(partial, BackupCodec.decode(BackupCodec.encode(partial)).data)
    }
    @Test fun invalidCompletedSetIndicesAreRejected() {
        val original = data()
        listOf(listOf(0), listOf(0, 0), listOf(0, 1, 2)).forEach { invalid ->
            val modified = original.copy(executions = listOf(original.executions.single().copy(completedSets = invalid)))
            assertTrue(runCatching { Rules.validate(modified, LocalDate.parse(day)) }.isFailure)
        }
    }
    @Test fun csvEscapesQuotesNewlinesAndSpreadsheetFormulas() {
        val result = CsvExport.weights(FitnessData(weights = listOf(WeightEntry(date = day, grams = 80005, note = "=SUM(1,2)\n\"备注\""))))
        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains("\"80.005\",\"160.01\""))
        assertTrue(result.contains("'=SUM(1,2)\n\"\"备注\"\""))
    }
    @Test fun trainingCsvDistinguishesConfirmedAndPendingSets() {
        val draft = data(ExecutionStatus.DRAFT).let { it.copy(executions = listOf(it.executions.single().copy(completedSets = listOf(0)))) }
        val result = CsvExport.training(draft)
        assertTrue(result.contains("已完成"))
        assertTrue(result.contains("未确认组"))
    }
    @Test fun recoveryIsBoundedAndRejectsUnlistedPaths() {
        val store = RecoveryStore(folder.newFolder())
        val original = data()
        repeat(10) { store.save(original, force = true) }
        assertEquals(8, store.points().size)
        assertEquals(original.plans, BackupCodec.decode(store.read(store.points().first().name)).data.plans)
        assertTrue(runCatching { store.read("../outside.json") }.isFailure)
    }
    @Test fun emptyInstallationDoesNotCreateRecoveryData() {
        val store = RecoveryStore(folder.newFolder())
        store.save(FitnessData())
        assertTrue(store.points().isEmpty())
    }
}
