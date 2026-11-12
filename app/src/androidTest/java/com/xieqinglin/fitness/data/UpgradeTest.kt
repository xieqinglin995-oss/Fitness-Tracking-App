package com.xieqinglin.fitness.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xieqinglin.fitness.backup.BackupCodec
import com.xieqinglin.fitness.data.local.FitnessDatabase
import com.xieqinglin.fitness.data.repository.FitnessRepository
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FitnessDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun versionOneUpgradesWithoutChangingWeightOrUnits() = runBlocking {
        val name = "upgrade-1-2"
        migration.createDatabase(name, 1).apply {
            execSQL("INSERT INTO weights (id,date,payload) VALUES ('w','2026-01-01','{\"id\":\"w\",\"date\":\"2026-01-01\",\"grams\":80125,\"note\":\"before upgrade\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-01T00:00:00Z\"}')")
            execSQL("INSERT INTO settings (id,payload) VALUES (1,'{\"unit\":\"JIN\",\"goalGrams\":75000,\"loadUnit\":\"KG\"}')")
            close()
        }
        migration.runMigrationsAndValidate(name, 2, true, FitnessDatabase.MIGRATION_1_2).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, FitnessDatabase::class.java, name).addMigrations(FitnessDatabase.MIGRATION_1_2).build()
        try {
            val repo = FitnessRepository(db)
            val data = repo.snapshot()
            assertEquals(80125L, data.weights.single().grams)
            assertEquals("before upgrade", data.weights.single().note)
            assertEquals(WeightUnit.JIN, data.settings.unit); assertEquals(WeightUnit.KG, data.settings.loadUnit)
            repo.mutate { it.copy(settings = it.settings.copy(sessions = listOf(SessionRecord("2026-01-01", "2026-01-01T00:00:00Z", "partial")))) }
            val exported = BackupCodec.encode(repo.snapshot())
            repo.restore(exported)
            assertEquals("partial", repo.snapshot().settings.sessions.single().note)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun rollingClockBackDoesNotBlockOtherRecordsButRejectsNewFutureDates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FitnessDatabase::class.java).build()
        try {
            val original = FitnessRepository(db)
            original.saveWeight(WeightEntry(date = "2026-01-02", grams = 80000), null, false)
            val clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
            val rolled = FitnessRepository(db, clock)
            rolled.saveWeight(WeightEntry(date = "2026-01-01", grams = 80500), null, false)
            rolled.mutate { it.copy(settings = it.settings.copy(unit = WeightUnit.JIN)) }
            assertEquals(2, rolled.snapshot().weights.size)
            assertTrue(runCatching { rolled.saveWeight(WeightEntry(date = "2026-01-03", grams = 81000), null, false) }.isFailure)
            assertEquals(2, rolled.snapshot().weights.size)
        } finally { db.close() }
    }

    @Test fun removalBeyondSqliteParameterLimitIsAtomic() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitnessDatabase::class.java).build()
        try {
            val repo = FitnessRepository(db)
            val items = (1..1100).map { Exercise(id = "item-$it", name = "拉伸 $it", kind = ExerciseKind.DURATION, target = Values(seconds = 60.0)) }
            val records = items.map { item -> Execution(date = "2026-01-01", exerciseId = item.id, nameSnapshot = item.name, kindSnapshot = item.kind, targetSnapshot = item.target, actual = item.target, status = ExecutionStatus.DONE) }
            repo.mutate { FitnessData(plans = listOf(DailyPlan(date = "2026-01-01", exercises = items)), executions = records) }
            repo.mutate { FitnessData() }
            assertTrue(repo.snapshot().plans.isEmpty()); assertTrue(repo.snapshot().executions.isEmpty())
        } finally { db.close() }
    }
}
