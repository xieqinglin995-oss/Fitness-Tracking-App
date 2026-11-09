package com.xieqinglin.fitness

import android.app.Application
import androidx.room.Room
import com.xieqinglin.fitness.data.local.FitnessDatabase
import com.xieqinglin.fitness.data.repository.FitnessRepository

class FitnessApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, FitnessDatabase::class.java, "fitness.db").build()
    }
    val repository by lazy { FitnessRepository(database) }
}
