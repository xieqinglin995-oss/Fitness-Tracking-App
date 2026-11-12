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
import android.os.SystemClock
import android.provider.Settings
import com.xieqinglin.fitness.backup.CsvExport
import com.xieqinglin.fitness.backup.RecoveryPoint
import com.xieqinglin.fitness.platform.ComfortOptions
import com.xieqinglin.fitness.platform.Reminders

data class ScreenState(val data: FitnessData = FitnessData(), val loaded: Boolean = false, val error: String? = null)
data class Notice(val text: String, val undo: CompletionUndo? = null)

class FitnessViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as FitnessApplication).repository
    private val app = application as FitnessApplication
    val comfort = app.comfort.state
    private val _state = MutableStateFlow(ScreenState())
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _preview = MutableStateFlow<Backup?>(null)
    val preview = _preview.asStateFlow()
    private var importText: String? = null
    private val notices = Channel<Notice>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private val preferences = application.getSharedPreferences("device_preferences", 0)
    private val _lastBackup = MutableStateFlow(preferences.getString("last_backup", null))
    val lastBackup = _lastBackup.asStateFlow()
    private val recoveryPoints = MutableStateFlow<List<RecoveryPoint>>(emptyList())
    val recoveries = recoveryPoints.asStateFlow()
    private val boot = Settings.Global.getInt(application.contentResolver, Settings.Global.BOOT_COUNT, 0)
    private val timerState = MutableStateFlow(if (preferences.getInt("timer_boot", -1) == boot)
        RestClock(preferences.getLong("rest_deadline", 0), preferences.getLong("rest_paused", 0)).let {
            if (it.running && it.deadline <= SystemClock.elapsedRealtime()) RestClock() else it
        } else RestClock())
    val timer = timerState.asStateFlow()
    private val mutex = Mutex()
    private var observing: Job? = null

    init { reload(); viewModelScope.launch(Dispatchers.IO) { recoveryPoints.value = app.recovery.points() }; Reminders.schedule(application) }

    fun reload() {
        observing?.cancel()
        observing = viewModelScope.launch {
            repository.data.catch { _state.value = ScreenState(error = "读取本地数据失败：${it.message}") }
                .collect { _state.value = ScreenState(it, loaded = true) }
        }
    }

    fun action(message: String = "已保存", onSuccess: () -> Unit = {}, undo: () -> CompletionUndo? = { null }, notify: Boolean = true,
        protect: Boolean = true, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                var recoveryFailed = false
                mutex.withLock {
                    if (protect) withContext(Dispatchers.IO) {
                        try { app.recovery.save(repository.snapshot()) }
                        catch (error: Exception) { android.util.Log.w("FitnessRecovery", "Recovery snapshot failed", error); recoveryFailed = true }
                        recoveryPoints.value = app.recovery.points()
                    }
                    block()
                }
                onSuccess()
                if (notify) notices.send(Notice(if (recoveryFailed) "$message；本机恢复点未更新，请手动备份" else message, undo()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { notices.send(Notice(error.message ?: "操作失败，请重试")) }
            finally { _busy.value = false }
        }
    }

    fun change(message: String = "已保存", onSuccess: () -> Unit = {}, transform: (FitnessData) -> FitnessData) =
        action(message, onSuccess) { repository.mutate(transform) }

    fun completeTraining(date: String, ids: Set<String>, onSuccess: () -> Unit = {}) {
        var token: CompletionUndo? = null
        action("所选训练已完成", onSuccess, undo = { token }) { token = repository.completeTraining(date, ids) }
    }

    fun undoCompletion(token: CompletionUndo) = action("已撤销本次完成") { repository.undoCompletion(token) }
    fun inform(text: String) { viewModelScope.launch { notices.send(Notice(text)) } }

    fun setComfort(transform: (ComfortOptions) -> ComfortOptions) {
        app.comfort.update(transform)
        Reminders.schedule(app)
    }

    private fun setTimer(next: RestClock) {
        timerState.value = next
        preferences.edit().putInt("timer_boot", boot).putLong("rest_deadline", next.deadline).putLong("rest_paused", next.pausedMillis).apply()
    }
    fun startRest(seconds: Int = comfort.value.restSeconds) = setTimer(RestClock().start(seconds, SystemClock.elapsedRealtime()))
    fun pauseRest() = setTimer(timerState.value.pause(SystemClock.elapsedRealtime()))
    fun resumeRest() = setTimer(timerState.value.resume(SystemClock.elapsedRealtime()))
    fun adjustRest(seconds: Int) = setTimer(timerState.value.adjust(seconds, SystemClock.elapsedRealtime()))
    fun cancelRest() = setTimer(RestClock())
    fun finishRest() { setTimer(RestClock()); viewModelScope.launch { notices.send(Notice("休息结束，按自己的节奏继续")) } }

    fun saveSession(date: String, item: Exercise, actual: Values, completed: List<Int>, finish: Boolean = false, onSuccess: () -> Unit = {}) {
        val done = finish || (item.kind == ExerciseKind.STRENGTH && completed.size == actual.sets.size)
        action(if (done) "动作已完成" else "进度已保存", onSuccess = { if (done) cancelRest(); onSuccess() }, notify = false) {
            repository.saveExecution(date, item, actual, if (done) ExecutionStatus.DONE else ExecutionStatus.DRAFT, "", completed)
        }
    }

    fun exportCsv(uri: Uri, training: Boolean) = action("表格已保存到所选位置", protect = false) {
        val data = repository.snapshot()
        write(uri, withContext(Dispatchers.Default) { if (training) CsvExport.training(data) else CsvExport.weights(data) })
    }

    fun inspectRecovery(name: String) = action("恢复点校验通过，请核对范围", protect = false) {
        val text = withContext(Dispatchers.IO) { app.recovery.read(name) }
        val backup = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        importText = text; _preview.value = backup
    }

    private suspend fun markBackup() = withContext(Dispatchers.IO) {
        val time = now()
        require(preferences.edit().putString("last_backup", time).commit()) { "备份文件已写出，但备份时间保存失败" }
        _lastBackup.value = time
    }

    fun export(uri: Uri) = action("备份已保存到所选位置", protect = false) {
        val data = repository.snapshot()
        val text = withContext(Dispatchers.Default) { BackupCodec.encode(data) }
        write(uri, text)
        markBackup()
    }

    fun inspect(uri: Uri) = action("备份校验通过，请核对恢复范围", protect = false) {
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

    fun restoreWithSafetyCopy(uri: Uri) = action("恢复完成，原数据已备份", onSuccess = ::cancelImport) {
        val text = requireNotNull(importText) { "请重新选择备份文件" }
        // Finish writing the user-accessible safety copy before starting the replacement transaction.
        val data = repository.snapshot()
        write(uri, withContext(Dispatchers.Default) { BackupCodec.encode(data) })
        markBackup()
        repository.restore(text)
    }

    private suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选位置，请重试" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(text); it.flush() }
    }
}
