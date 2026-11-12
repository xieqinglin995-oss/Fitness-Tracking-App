package com.xieqinglin.fitness.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime

object Rules {
    fun date(value: String): LocalDate {
        val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
        require(parsed != null && parsed.toString() == value && value.length == 10) { "日期格式应为 YYYY-MM-DD" }
        return parsed
    }

    fun pastDate(value: String, today: LocalDate = LocalDate.now()) {
        require(!date(value).isAfter(today)) { "不能记录未来日期的体重或实际训练" }
    }

    fun weight(text: String, unit: WeightUnit, allowZero: Boolean = false): Long {
        require(Regex("[0-9]+(\\.[0-9]{1,2})?").matches(text.trim())) { "请输入最多两位小数的有效数值" }
        val value = text.trim().toBigDecimal()
        require(if (allowZero) value >= BigDecimal.ZERO else value > BigDecimal.ZERO) { "数值必须大于 0" }
        return try {
            value.multiply(BigDecimal(if (unit == WeightUnit.KG) 1000 else 500)).longValueExact()
        } catch (_: ArithmeticException) { throw IllegalArgumentException("数值过大，请核对输入") }
    }

    fun values(kind: ExerciseKind, value: Values, actual: Boolean = false) {
        fun positive(number: Double?) = number != null && number.isFinite() && if (actual) number >= 0 else number > 0
        when (kind) {
            ExerciseKind.STRENGTH -> {
                require(value.sets.isNotEmpty() && value.sets.size <= 100) { "请输入 1–100 组数据" }
                require(value.sets.all { (if (actual) it.reps >= 0 else it.reps > 0) && (it.loadGrams == null || it.loadGrams >= 0) }) { "次数须为${if (actual) "非负" else "正"}整数，负重不能小于 0" }
                require(value.sets.map { it.id }.filter { it.isNotBlank() }.let { it.distinct().size == it.size }) { "训练组标识重复" }
                require(value.sets.all { it.id.isBlank() } || value.sets.all { it.id.isNotBlank() }) { "训练组标识不完整" }
                require(value.seconds == null && value.kilometers == null) { "次数型项目不能包含时长或距离字段" }
            }
            ExerciseKind.DURATION -> {
                require(positive(value.seconds)) { "请输入有效时长（分钟）" }
                require(value.sets.isEmpty() && value.kilometers == null) { "时长型项目字段不匹配" }
            }
            ExerciseKind.DISTANCE -> {
                require(positive(value.kilometers)) { "请输入有效距离（km）" }
                require(value.seconds == null || positive(value.seconds)) { "时长必须是有效数值" }
                require(value.sets.isEmpty()) { "距离型项目不能包含组次字段" }
            }
        }
    }

    fun exercise(item: Exercise) {
        require(item.id.isNotBlank() && item.name.isNotBlank() && item.name.length <= 100) { "项目名称须为 1–100 字" }
        require(item.note.length <= 500) { "备注不能超过 500 字" }
        values(item.kind, item.target)
    }

    fun validate(data: FitnessData, today: LocalDate = LocalDate.now(), allowFutureHistory: Boolean = false) {
        fun unique(ids: List<String>, name: String) {
            require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "$name 存在重复或空标识" }
        }
        fun timestamp(value: String) { require(runCatching { OffsetDateTime.parse(value) }.isSuccess) { "时间戳必须包含时区" } }
        unique(data.weights.map { it.id }, "体重")
        unique(data.weights.map { it.date }, "体重日期")
        data.weights.forEach {
            if (allowFutureHistory) date(it.date) else pastDate(it.date, today)
            require(it.grams > 0 && it.note.length <= 500) { "体重或备注不合法" }
            timestamp(it.createdAt); timestamp(it.updatedAt)
        }
        unique(data.plans.map { it.id }, "计划")
        unique(data.plans.map { it.date }, "计划日期")
        val items = data.plans.flatMap { it.exercises }
        unique(items.map { it.id }, "计划项目")
        data.plans.forEach {
            date(it.date)
            require(it.name.isNotBlank() && it.name.length <= 100) { "计划名称不合法" }
            require(!it.rest || it.exercises.isEmpty()) { "休息日不能包含训练项目" }
            it.exercises.forEach(::exercise)
        }
        unique(data.executions.map { it.id }, "实际记录")
        unique(data.executions.map { it.exerciseId }, "实际记录关联项目")
        val itemMap = data.plans.flatMap { p -> p.exercises.map { it.id to (p.date to it) } }.toMap()
        data.executions.forEach {
            if (allowFutureHistory) date(it.date) else pastDate(it.date, today)
            val parent = itemMap[it.exerciseId]
            require(parent != null && parent.first == it.date) { "实际记录缺少同日期的关联项目" }
            require(it.nameSnapshot.isNotBlank() && it.nameSnapshot.length <= 100 && it.skipReason.length <= 500) { "实际记录名称或跳过原因不合法" }
            require(parent.second.kind == it.kindSnapshot) { "实际记录与关联项目的记录方式不一致" }
            values(it.kindSnapshot, it.targetSnapshot)
            values(it.kindSnapshot, it.actual, actual = true)
            require(it.completedSets.distinct().size == it.completedSets.size && it.completedSets.all { index -> index in it.actual.sets.indices }) { "已完成组数不合法" }
            require(it.kindSnapshot == ExerciseKind.STRENGTH || it.completedSets.isEmpty()) { "该项目不包含组次" }
            require(it.status != ExecutionStatus.DONE || it.kindSnapshot != ExerciseKind.STRENGTH || it.completedSets.toSet() == it.actual.sets.indices.toSet()) { "已完成项目须包含全部训练组" }
            require(it.status != ExecutionStatus.SKIPPED || it.completedSets.isEmpty()) { "跳过项目不能包含已完成组" }
            require(it.note.length <= 500) { "备注不能超过 500 字" }
            it.completedSetIds?.let { ids ->
                require(ids == it.completedSets.map { index -> it.actual.sets[index].id } && ids.all(String::isNotBlank)) { "确认组标识与实际不一致" }
            }
            timestamp(it.updatedAt)
        }
        unique(data.templates.map { it.id }, "模板")
        data.templates.forEach {
            require(it.name.isNotBlank() && it.name.length <= 100 && it.exercises.isNotEmpty()) { "模板须有名称和训练项目" }
            unique(it.exercises.map { e -> e.id }, "模板项目")
            it.exercises.forEach { e -> exercise(e); require(!e.temporary) { "模板不能含临时状态" } }
        }
        require(data.settings.goalGrams == null || data.settings.goalGrams > 0) { "目标体重必须大于 0" }
        unique(data.settings.library.map { it.id }, "动作库")
        data.settings.library.forEach { require(it.name.isNotBlank() && it.name.length <= 100 && it.cue.length <= 500) { "动作提示不合法" } }
        unique(data.settings.sessions.map { it.date }, "训练总结日期")
        data.settings.sessions.forEach { date(it.date); timestamp(it.endedAt); require(it.note.length <= 500) { "训练备注过长" } }
        unique(data.settings.sources.map { it.date }, "模板来源日期")
        data.settings.sources.forEach { date(it.date); require(it.templateId.isNotBlank()) { "模板来源不合法" } }
    }
}

fun formatWeight(grams: Long, unit: WeightUnit): String = BigDecimal(grams)
    .divide(BigDecimal(if (unit == WeightUnit.KG) 1000 else 500), 2, RoundingMode.HALF_UP).toPlainString()

fun formatDelta(grams: Double, unit: WeightUnit): String {
    val result = BigDecimal.valueOf(grams).divide(BigDecimal(if (unit == WeightUnit.KG) 1000 else 500), 2, RoundingMode.HALF_UP)
    return (if (result.signum() > 0) "+" else "") + (if (result.signum() == 0) "0.00" else result.toPlainString())
}

fun formatNumber(number: Double): String = BigDecimal.valueOf(number).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun Values.description(kind: ExerciseKind, unit: WeightUnit): String = when (kind) {
    ExerciseKind.STRENGTH -> sets.mapIndexed { index, set -> "${index + 1}组 ${set.reps}次" + (set.loadGrams?.let { " × ${formatWeight(it, unit)} ${unit.label}" } ?: "") }.joinToString(" / ")
    ExerciseKind.DURATION -> durationLabel(seconds ?: 0.0)
    ExerciseKind.DISTANCE -> "${formatNumber(kilometers ?: 0.0)} km" + (seconds?.let { " · ${durationLabel(it)}" } ?: "")
}

fun Values.summary(kind: ExerciseKind, unit: WeightUnit): String = when {
    kind != ExerciseKind.STRENGTH -> description(kind, unit)
    sets.map { it.reps to it.loadGrams }.distinct().size == 1 -> "${sets.size} 组 × ${sets.first().reps} 次" +
        (sets.first().loadGrams?.let { " · ${formatWeight(it, unit)} ${unit.label}" } ?: "")
    else -> "${sets.size} 组 · ${sets.joinToString(" / ") { it.reps.toString() }} 次"
}
