package com.xieqinglin.fitness.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.xieqinglin.fitness.data.repository.FitnessRepository
import com.xieqinglin.fitness.domain.FitnessData
import com.xieqinglin.fitness.domain.now
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.UUID

data class AutomaticBackupState(val directory: String?, val last: String?, val pending: Boolean = false, val error: Boolean = false)

class AutomaticBackup(private val context: Context, private val repository: FitnessRepository) {
    private val prefs = context.getSharedPreferences("automatic_backup", Context.MODE_PRIVATE)
    private val stateValue = MutableStateFlow(AutomaticBackupState(prefs.getString("directory", null), prefs.getString("last", null)))
    val state = stateValue.asStateFlow()
    private val directories = MutableStateFlow(prefs.getString("directory", null))
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    @Volatile private var latest: FitnessData? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            directories.collectLatest { directory ->
                if (directory != null) repository.data
                    .onEach { latest = it; stateValue.value = stateValue.value.copy(pending = true) }
                    .debounce(3000)
                    .catch { stateValue.value = stateValue.value.copy(pending = false, error = true) }
                    .collect { data ->
                        try { save(data) } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* State exposes failure; main recording remains available. */ }
                    }
            }
        }
    }

    suspend fun choose(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        mutex.withLock {
            val old = prefs.getString("owned_directory", prefs.getString("directory", null))
            val editor = prefs.edit().putString("directory", uri.toString()).putString("owned_directory", uri.toString())
            if (old != uri.toString()) editor.remove("owned").remove("last")
            require(editor.commit()) { "目录授权保存失败" }
            stateValue.value = AutomaticBackupState(uri.toString(), prefs.getString("last", null), pending = true)
            directories.value = uri.toString()
            if (old == uri.toString()) scope.launch { runCatching { save(repository.snapshot()) } }
        }
    }

    suspend fun disable() = withContext(Dispatchers.IO) {
        // Cancel future collection first; never delete the user's existing backup files.
        directories.value = null
        mutex.withLock {
            prefs.getString("directory", null)?.let { prefs.edit().putString("owned_directory", it).apply() }
            require(prefs.edit().remove("directory").commit()) { "关闭自动备份失败" }
            stateValue.value = AutomaticBackupState(null, prefs.getString("last", null))
        }
    }

    suspend fun save(data: FitnessData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val directory = prefs.getString("directory", null) ?: error("请先选择备份目录")
            val tree = Uri.parse(directory)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            var created: Uri? = null
            try {
                val text = BackupCodec.encode(data)
                require(text.toByteArray(Charsets.UTF_8).size <= BackupCodec.MAX_BYTES) { "备份文件超过容量限制" }
                created = requireNotNull(DocumentsContract.createDocument(context.contentResolver, parent, "application/json",
                    "haoh-auto-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")) { "无法创建备份" }
                requireNotNull(context.contentResolver.openOutputStream(created, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(text); it.flush() }
                val readBack = requireNotNull(context.contentResolver.openInputStream(created)).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        require(output.size() + n <= BackupCodec.MAX_BYTES) { "备份校验失败" }; output.write(buffer, 0, n) }
                    output.toString("UTF-8")
                }
                require(BackupCodec.decode(readBack, allowFutureHistory = true).data == data) { "备份回读不一致" }
                val owned = prefs.getString("owned", "").orEmpty().lines().filter(String::isNotBlank)
                val current = listOf(created.toString()) + owned
                val timestamp = now()
                require(prefs.edit().putString("owned", current.take(8).joinToString("\n")).putString("last", timestamp).commit()) { "备份状态保存失败" }
                created = null
                stateValue.value = AutomaticBackupState(directory, timestamp, pending = latest?.let { it != data } == true)
                // Only URIs created and registered by this installation are eligible for rotation.
                current.drop(8).forEach { old -> runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(old)) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                stateValue.value = stateValue.value.copy(pending = false, error = true)
                throw IllegalStateException("自动备份未完成，请检查授权、位置或可用空间", error)
            } finally { created?.let { runCatching { DocumentsContract.deleteDocument(context.contentResolver, it) } } }
        }
    }
}
