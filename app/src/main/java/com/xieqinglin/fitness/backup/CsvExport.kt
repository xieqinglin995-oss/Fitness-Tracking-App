package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.*
import java.math.BigDecimal

object CsvExport {
    private fun kilograms(grams: Long) = BigDecimal(grams).movePointLeft(3).stripTrailingZeros().toPlainString()
    private fun jin(grams: Long) = BigDecimal(grams).divide(BigDecimal(500)).stripTrailingZeros().toPlainString()
    private fun row(vararg cells: String): String = cells.joinToString(",") { value ->
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') && value.toDoubleOrNull() == null) "'$value" else value
        "\"${safe.replace("\"", "\"\"")}\""
    }
    fun weights(data: FitnessData): String = "\uFEFF" + (listOf(row("日期", "体重公斤", "体重斤", "备注")) + data.weights.sortedBy { it.date }.map {
        row(it.date, kilograms(it.grams), jin(it.grams), it.note)
    }).joinToString("\r\n")
    fun training(data: FitnessData): String {
        val rows = mutableListOf(row("日期", "项目", "类型", "状态", "组", "次数", "负重公斤", "时长秒", "距离公里", "跳过原因"))
        data.executions.sortedBy { it.date }.forEach { record ->
            val status = when (record.status) { ExecutionStatus.DONE -> "已完成"; ExecutionStatus.DRAFT -> "进行中"; ExecutionStatus.SKIPPED -> "已跳过" }
            if (record.kindSnapshot == ExerciseKind.STRENGTH) record.actual.sets.forEachIndexed { index, set ->
                rows += row(record.date, record.nameSnapshot, record.kindSnapshot.label,
                    if (index in record.completedSets) "已完成" else status + " 未确认组", (index + 1).toString(), set.reps.toString(),
                    set.loadGrams?.let(::kilograms).orEmpty(), "", "", record.skipReason)
            } else rows += row(record.date, record.nameSnapshot, record.kindSnapshot.label, status, "", "", "",
                record.actual.seconds?.let(::formatNumber).orEmpty(), record.actual.kilometers?.let(::formatNumber).orEmpty(), record.skipReason)
        }
        return "\uFEFF" + rows.joinToString("\r\n")
    }
}
