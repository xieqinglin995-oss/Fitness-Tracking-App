package com.xieqinglin.fitness.data.local

import androidx.room.*
import com.xieqinglin.fitness.backup.fitnessJson
import com.xieqinglin.fitness.domain.*
import kotlinx.serialization.encodeToString

@Entity(tableName = "weights", indices = [Index(value = ["date"], unique = true)])
data class WeightRow(@PrimaryKey val id: String, val date: String, val payload: String)

@Entity(tableName = "plans", indices = [Index(value = ["date"], unique = true)])
data class PlanRow(@PrimaryKey val id: String, val date: String, val name: String, val rest: Boolean)

@Entity(tableName = "items", foreignKeys = [ForeignKey(entity = PlanRow::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("planId")])
data class ItemRow(@PrimaryKey val id: String, val planId: String, val position: Int, val payload: String)

@Entity(tableName = "actuals", foreignKeys = [ForeignKey(entity = ItemRow::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["exerciseId"], unique = true)])
data class ActualRow(@PrimaryKey val id: String, val exerciseId: String, val payload: String)

@Entity(tableName = "templates")
data class TemplateRow(@PrimaryKey val id: String, val payload: String)

@Entity(tableName = "settings")
data class SettingsRow(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
abstract class FitnessDao {
    @Query("SELECT * FROM weights ORDER BY date") abstract suspend fun weights(): List<WeightRow>
    @Query("SELECT * FROM plans ORDER BY date") abstract suspend fun plans(): List<PlanRow>
    @Query("SELECT * FROM items ORDER BY position") abstract suspend fun items(): List<ItemRow>
    @Query("SELECT * FROM actuals") abstract suspend fun actuals(): List<ActualRow>
    @Query("SELECT * FROM templates") abstract suspend fun templates(): List<TemplateRow>
    @Query("SELECT * FROM settings WHERE id = 1") abstract suspend fun settings(): SettingsRow?
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun putWeights(rows: List<WeightRow>)
    @Update abstract suspend fun updateWeights(rows: List<WeightRow>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun putPlans(rows: List<PlanRow>)
    @Update abstract suspend fun updatePlans(rows: List<PlanRow>)
    @Upsert abstract suspend fun putItems(rows: List<ItemRow>)
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun putActuals(rows: List<ActualRow>)
    @Update abstract suspend fun updateActuals(rows: List<ActualRow>)
    @Upsert abstract suspend fun putTemplates(rows: List<TemplateRow>)
    @Upsert abstract suspend fun putSettings(row: SettingsRow)
    @Query("DELETE FROM actuals WHERE id IN (:ids)") abstract suspend fun deleteActuals(ids: List<String>)
    @Query("DELETE FROM items WHERE id IN (:ids)") abstract suspend fun deleteItems(ids: List<String>)
    @Query("DELETE FROM plans WHERE id IN (:ids)") abstract suspend fun deletePlans(ids: List<String>)
    @Query("DELETE FROM weights WHERE id IN (:ids)") abstract suspend fun deleteWeights(ids: List<String>)
    @Query("DELETE FROM templates WHERE id IN (:ids)") abstract suspend fun deleteTemplates(ids: List<String>)
    @Query("DELETE FROM actuals") abstract suspend fun clearActuals()
    @Query("DELETE FROM items") abstract suspend fun clearItems()
    @Query("DELETE FROM plans") abstract suspend fun clearPlans()
    @Query("DELETE FROM weights") abstract suspend fun clearWeights()
    @Query("DELETE FROM templates") abstract suspend fun clearTemplates()

    @Transaction
    open suspend fun snapshot(): FitnessData {
        val byPlan = items().groupBy { it.planId }
        return FitnessData(
            weights().map { fitnessJson.decodeFromString(it.payload) },
            plans().map { p -> DailyPlan(p.id, p.date, p.name, p.rest, byPlan[p.id].orEmpty().map { fitnessJson.decodeFromString(it.payload) }) },
            actuals().map { fitnessJson.decodeFromString(it.payload) },
            templates().map { fitnessJson.decodeFromString(it.payload) },
            settings()?.let { fitnessJson.decodeFromString(it.payload) } ?: Settings(),
        )
    }

    suspend fun write(data: FitnessData, previous: FitnessData?) {
        // Diff by stable IDs. Do not use REPLACE: SQLite REPLACE deletes rows and can break historical links.
        if (previous == null) {
            clearActuals(); clearItems(); clearPlans(); clearWeights(); clearTemplates()
        } else {
            deleteActuals(previous.executions.map { it.id } - data.executions.map { it.id }.toSet())
            deleteItems(previous.plans.flatMap { it.exercises }.map { it.id } - data.plans.flatMap { it.exercises }.map { it.id }.toSet())
            deletePlans(previous.plans.map { it.id } - data.plans.map { it.id }.toSet())
            deleteWeights(previous.weights.map { it.id } - data.weights.map { it.id }.toSet())
            deleteTemplates(previous.templates.map { it.id } - data.templates.map { it.id }.toSet())
        }
        val oldWeights = previous?.weights.orEmpty().associateBy { it.id }
        val oldPlans = previous?.plans.orEmpty().associateBy { it.id }
        val oldItems = previous?.plans.orEmpty().flatMap { p -> p.exercises.mapIndexed { i, e -> e.id to Triple(p.id, i, e) } }.toMap()
        val oldActuals = previous?.executions.orEmpty().associateBy { it.id }
        val oldTemplates = previous?.templates.orEmpty().associateBy { it.id }
        val weights = data.weights.filter { oldWeights[it.id] != it }.map { WeightRow(it.id, it.date, fitnessJson.encodeToString(it)) }
        putWeights(weights.filterNot { it.id in oldWeights })
        updateWeights(weights.filter { it.id in oldWeights })
        val plans = data.plans.filter { oldPlans[it.id]?.let { old -> old.date == it.date && old.name == it.name && old.rest == it.rest } != true }.map { PlanRow(it.id, it.date, it.name, it.rest) }
        putPlans(plans.filterNot { it.id in oldPlans })
        updatePlans(plans.filter { it.id in oldPlans })
        putItems(data.plans.flatMap { p -> p.exercises.mapIndexedNotNull { i, e -> if (oldItems[e.id] == Triple(p.id, i, e)) null else ItemRow(e.id, p.id, i, fitnessJson.encodeToString(e)) } })
        val actuals = data.executions.filter { oldActuals[it.id] != it }.map { ActualRow(it.id, it.exerciseId, fitnessJson.encodeToString(it)) }
        putActuals(actuals.filterNot { it.id in oldActuals })
        updateActuals(actuals.filter { it.id in oldActuals })
        putTemplates(data.templates.filter { oldTemplates[it.id] != it }.map { TemplateRow(it.id, fitnessJson.encodeToString(it)) })
        if (previous?.settings != data.settings) putSettings(SettingsRow(payload = fitnessJson.encodeToString(data.settings)))
    }
}

@Database(entities = [WeightRow::class, PlanRow::class, ItemRow::class, ActualRow::class, TemplateRow::class, SettingsRow::class], version = 1, exportSchema = true)
abstract class FitnessDatabase : RoomDatabase() { abstract fun dao(): FitnessDao }
