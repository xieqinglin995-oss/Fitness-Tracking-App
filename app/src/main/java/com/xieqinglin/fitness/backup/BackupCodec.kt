package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime

val fitnessJson = Json { encodeDefaults = true; prettyPrint = false; isLenient = false }

@Serializable data class RecordCounts(val weights: Int, val plans: Int, val executions: Int, val templates: Int)
@Serializable data class Backup(
    val schemaVersion: Int,
    val exportedAt: String,
    val counts: RecordCounts,
    val data: FitnessData,
)

object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    fun counts(data: FitnessData) = RecordCounts(data.weights.size, data.plans.size, data.executions.size, data.templates.size)
    fun encode(data: FitnessData): String = fitnessJson.encodeToString(Backup(3, now(), counts(data), data))

    fun decode(text: String, today: LocalDate = LocalDate.now()): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件不能超过 32 MB" }
        var root = fitnessJson.parseToJsonElement(text).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
        require(version in 1..3) { "备份版本不兼容，支持 schemaVersion 1 至 3" }
        if (version == 1) {
            val data = requireNotNull(root["data"]).jsonObject
            val settings = requireNotNull(data["settings"]).jsonObject
            require("loadUnit" !in settings) { "v1 备份包含不支持的字段" }
            root = JsonObject(root + ("data" to JsonObject(data + ("settings" to JsonObject(settings +
                ("loadUnit" to requireNotNull(settings["unit"]) { "备份缺少单位" }))))))
        }
        if (version != 3) {
            val data = requireNotNull(root["data"]).jsonObject
            val executions = requireNotNull(data["executions"]) as JsonArray
            val migrated = executions.map { element ->
                val record = element.jsonObject
                require("completedSets" !in record) { "旧版备份包含不支持的训练组字段" }
                val complete = record["kindSnapshot"]?.jsonPrimitive?.content == "STRENGTH" && record["status"]?.jsonPrimitive?.content == "DONE"
                val sets = record["actual"]?.jsonObject?.get("sets") as? JsonArray
                val indices = if (complete) requireNotNull(sets).indices.map { JsonPrimitive(it) } else emptyList()
                JsonObject(record + ("completedSets" to JsonArray(indices)))
            }
            root = JsonObject(root + ("data" to JsonObject(data + ("executions" to JsonArray(migrated)))))
        }
        val backup = fitnessJson.decodeFromJsonElement(Backup.serializer(), root)
        checkTypes(root, fitnessJson.parseToJsonElement(fitnessJson.encodeToString(backup)))
        OffsetDateTime.parse(backup.exportedAt)
        Rules.validate(backup.data, today)
        require(backup.counts == counts(backup.data)) { "备份记录数量校验失败" }
        return backup
    }

    private fun checkTypes(input: JsonElement, normalized: JsonElement) {
        when {
            input is JsonObject && normalized is JsonObject -> {
                require(input.keys == normalized.keys) { "备份缺少字段，请使用完整的导出文件" }
                input.forEach { (key, value) -> checkTypes(value, requireNotNull(normalized[key])) }
            }
            input is JsonArray && normalized is JsonArray -> input.zip(normalized).forEach { (a, b) -> checkTypes(a, b) }
            input is JsonPrimitive && normalized is JsonPrimitive -> require(input.isString == normalized.isString) { "备份字段类型错误：数字、布尔值与字符串不能混用" }
            else -> error("备份字段结构错误")
        }
    }
}
