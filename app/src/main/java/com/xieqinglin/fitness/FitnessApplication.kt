package com.xieqinglin.fitness

import android.app.Application
import androidx.room.Room
import com.xieqinglin.fitness.data.local.FitnessDatabase
import com.xieqinglin.fitness.data.repository.FitnessRepository
import com.xieqinglin.fitness.platform.ComfortPreferences
import com.xieqinglin.fitness.backup.RecoveryStore
import java.io.File
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

class FitnessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        automaticBackup.start()
        val manager = getSystemService(ShortcutManager::class.java)
        if (manager.dynamicShortcuts.map { it.id }.toSet() != setOf("weight", "training")) {
            manager.dynamicShortcuts = listOf("weight" to "记录体重", "training" to "开始训练").map { (action, title) ->
                ShortcutInfo.Builder(this, action).setShortLabel(title).setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("open_action", action)).build()
            }
        }
    }
    val database by lazy {
        Room.databaseBuilder(this, FitnessDatabase::class.java, "fitness.db").addMigrations(FitnessDatabase.MIGRATION_1_2).build()
    }
    val repository by lazy { FitnessRepository(database) }
    val comfort by lazy { ComfortPreferences(this) }
    val recovery by lazy { RecoveryStore(File(noBackupFilesDir, "recovery")) }
    val automaticBackup by lazy { com.xieqinglin.fitness.backup.AutomaticBackup(this, repository) }
}
