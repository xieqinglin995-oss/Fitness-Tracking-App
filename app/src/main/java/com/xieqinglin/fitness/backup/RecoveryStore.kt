package com.xieqinglin.fitness.backup

import com.xieqinglin.fitness.domain.FitnessData
import java.io.File
import java.util.UUID

data class RecoveryPoint(val name: String, val modified: Long)

class RecoveryStore(private val directory: File) {
    fun points(): List<RecoveryPoint> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "json" }.sortedByDescending { it.lastModified() }
        .map { RecoveryPoint(it.name, it.lastModified()) }

    fun save(data: FitnessData, force: Boolean = false) {
        if (data.weights.isEmpty() && data.plans.isEmpty() && data.templates.isEmpty()) return
        if (!force && points().firstOrNull()?.let { System.currentTimeMillis() - it.modified < 60_000 } == true) return
        directory.mkdirs()
        val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        val temporary = File(directory, target.name + ".tmp")
        try {
            temporary.writeText(BackupCodec.encode(data), Charsets.UTF_8)
            check(temporary.renameTo(target)) { "无法保存恢复点" }
        } finally { temporary.delete() }
        points().drop(8).forEach { File(directory, it.name).delete() }
    }

    fun read(name: String): String {
        require(points().any { it.name == name }) { "恢复点已不存在" }
        val file = File(directory, name).canonicalFile
        require(file.parentFile == directory.canonicalFile) { "无效的恢复点" }
        require(file.length() <= BackupCodec.MAX_BYTES) { "恢复点过大" }
        return file.readText(Charsets.UTF_8)
    }
}
