package com.xieqinglin.fitness.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xieqinglin.fitness.backup.BackupCodec
import com.xieqinglin.fitness.data.local.FitnessDatabase
import com.xieqinglin.fitness.data.local.WeightRow
import com.xieqinglin.fitness.data.repository.FitnessRepository
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: FitnessDatabase
    private lateinit var repo: FitnessRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitnessDatabase::class.java).build()
        repo = FitnessRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun datesAreUniqueEvenBelowRepository() = runBlocking {
        db.dao().putWeights(listOf(WeightRow("first", "2026-01-01", "{}")))
        assertTrue(runCatching { db.dao().putWeights(listOf(WeightRow("second", "2026-01-01", "{}"))) }.isFailure)
        assertEquals(1, db.dao().weights().size)
    }

    @Test fun failureHalfwayThroughReplacementRollsBackAllTables() = runBlocking {
        val weight = WeightEntry(date = "2026-01-01", grams = 80250)
        repo.saveWeight(weight, null, false)
        val original = repo.snapshot()
        val failed = runCatching {
            db.withTransaction {
                db.dao().write(FitnessData(settings = Settings(WeightUnit.JIN)), null)
                error("injected storage failure")
            }
        }
        assertTrue(failed.isFailure)
        assertEquals(original, repo.snapshot())
    }

    @Test fun invalidImportDoesNotModifyExistingData() = runBlocking {
        repo.saveWeight(WeightEntry(date = "2026-01-01", grams = 80250), null, false)
        val original = repo.snapshot()
        assertTrue(runCatching { repo.restore("{broken}") }.isFailure)
        assertEquals(original, repo.snapshot())
    }

    @Test fun validBackupRestoresAllObjectsAndForeignKeysProtectHistory() = runBlocking {
        val item = Exercise(name = "跑步", kind = ExerciseKind.DISTANCE, target = Values(kilometers = 3.0))
        val plan = DailyPlan(date = "2026-01-01", exercises = listOf(item))
        val actual = Execution(date = plan.date, exerciseId = item.id, nameSnapshot = item.name, kindSnapshot = item.kind,
            targetSnapshot = item.target, actual = Values(kilometers = 3.5), status = ExecutionStatus.DONE)
        val expected = FitnessData(weights = listOf(WeightEntry(date = plan.date, grams = 80250)), plans = listOf(plan),
            executions = listOf(actual), templates = listOf(WorkoutTemplate(name = "有氧", exercises = listOf(item.freshCopy()))), settings = Settings(WeightUnit.JIN, 75000))
        repo.restore(BackupCodec.encode(expected))
        assertEquals(expected, repo.snapshot())
        assertTrue(runCatching { db.dao().deleteItems(listOf(item.id)) }.isFailure)
        assertEquals(expected, repo.snapshot())
    }

    @Test fun planChangesDoNotRewriteActualSnapshot() = runBlocking {
        val item = Exercise(name = "深蹲", kind = ExerciseKind.STRENGTH, target = Values(sets = listOf(SetValue(10))))
        repo.saveExercise("2026-01-01", item)
        repo.saveExecution("2026-01-01", item, Values(sets = listOf(SetValue(12))), ExecutionStatus.DONE, "")
        repo.saveExercise("2026-01-01", item.copy(name = "徒手深蹲", target = Values(sets = listOf(SetValue(15)))))
        val restored = repo.snapshot()
        assertEquals("深蹲", restored.executions.single().nameSnapshot)
        assertEquals(10, restored.executions.single().targetSnapshot.sets.single().reps)
        assertEquals(12, restored.executions.single().actual.sets.single().reps)
    }

    @Test fun confirmedWeightDateMoveReplacesConflictWithoutDuplicates() = runBlocking {
        val first = WeightEntry(date = "2026-01-01", grams = 80250)
        val second = WeightEntry(date = "2026-01-02", grams = 80000)
        repo.saveWeight(first, null, false); repo.saveWeight(second, null, false)
        val original = repo.snapshot()
        assertTrue(runCatching { repo.saveWeight(first.copy(date = second.date), first.id, false) }.isFailure)
        assertEquals(original, repo.snapshot())
        repo.saveWeight(first.copy(date = second.date), first.id, true)
        assertEquals(listOf(first.copy(date = second.date)), repo.snapshot().weights)
    }

    @Test fun onDiskRecordsSurviveDatabaseReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${newId()}.db"
        var disk = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        try {
            val data = FitnessData(weights = listOf(WeightEntry(date = "2026-01-01", grams = 80250)), settings = Settings(WeightUnit.JIN))
            FitnessRepository(disk).restore(BackupCodec.encode(data))
            disk.close()
            disk = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
            assertEquals(data, FitnessRepository(disk).snapshot())
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
