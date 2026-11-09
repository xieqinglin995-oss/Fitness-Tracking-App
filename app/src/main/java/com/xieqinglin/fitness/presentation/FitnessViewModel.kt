package com.xieqinglin.fitness.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xieqinglin.fitness.FitnessApplication
import com.xieqinglin.fitness.backup.Backup
import com.xieqinglin.fitness.backup.BackupCodec
import com.xieqinglin.fitness.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream

data class ScreenState(val data: FitnessData = FitnessData(), val loaded: Boolean = false, val error: String? = null)

class FitnessViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as FitnessApplication).repository
    private val _state = MutableStateFlow(ScreenState())
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _preview = MutableStateFlow<Backup?>(null)
    val preview = _preview.asStateFlow()
    private var importText: String? = null
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private val mutex = Mutex()
    private var observing: Job? = null

    init { reload() }

    fun reload() {
        observing?.cancel()
        observing = viewModelScope.launch {
            repository.data.catch { _state.value = ScreenState(error = "读取本地数据失败：${it.message}") }
                .collect { _state.value = ScreenState(it, loaded = true) }
        }
    }

    fun action(message: String = "已保存", onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                mutex.withLock { block() }
                onSuccess()
                notices.send(message)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { notices.send(error.message ?: "操作失败，请重试") }
            finally { _busy.value = false }
        }
    }

    fun change(message: String = "已保存", onSuccess: () -> Unit = {}, transform: (FitnessData) -> FitnessData) =
        action(message, onSuccess) { repository.mutate(transform) }

    fun export(uri: Uri) = action("备份已保存到：$uri") {
        val data = repository.snapshot()
        val text = withContext(Dispatchers.Default) { BackupCodec.encode(data) }
        write(uri, text)
    }

    fun inspect(uri: Uri) = action("备份校验通过，请核对恢复范围") {
        val text = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openInputStream(uri)) { "无法打开所选文件" }.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= BackupCodec.MAX_BYTES) { "备份文件不能超过 32 MB" }
                    output.write(buffer, 0, read)
                }
                output.toString("UTF-8")
            }
        }
        val backup = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        importText = text
        _preview.value = backup
    }

    fun cancelImport() { importText = null; _preview.value = null }

    fun restoreWithSafetyCopy(uri: Uri) = action("恢复完成；原数据备份已保存到：$uri", onSuccess = ::cancelImport) {
        val text = requireNotNull(importText) { "请重新选择备份文件" }
        // Finish writing the user-accessible safety copy before starting the replacement transaction.
        val data = repository.snapshot()
        write(uri, withContext(Dispatchers.Default) { BackupCodec.encode(data) })
        repository.restore(text)
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选位置，请重试" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(text); it.flush() }
    }
}
