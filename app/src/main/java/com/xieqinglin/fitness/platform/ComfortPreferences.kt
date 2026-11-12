package com.xieqinglin.fitness.platform

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeChoice(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
data class ComfortOptions(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val haptics: Boolean = true,
    val keepAwake: Boolean = true,
    val restSeconds: Int = 60,
    val weightReminder: Boolean = false,
    val weightHour: Int = 8,
    val weightMinute: Int = 0,
    val trainingReminder: Boolean = false,
    val trainingHour: Int = 18,
    val trainingMinute: Int = 30,
)

class ComfortPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("comfort_options", Context.MODE_PRIVATE)
    private fun read() = ComfortOptions(
        theme = runCatching { ThemeChoice.valueOf(preferences.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM),
        haptics = preferences.getBoolean("haptics", true), keepAwake = preferences.getBoolean("keep_awake", true),
        restSeconds = preferences.getInt("rest_seconds", 60).coerceIn(0, 600),
        weightReminder = preferences.getBoolean("weight_reminder", false),
        weightHour = preferences.getInt("weight_hour", 8), weightMinute = preferences.getInt("weight_minute", 0),
        trainingReminder = preferences.getBoolean("training_reminder", false),
        trainingHour = preferences.getInt("training_hour", 18), trainingMinute = preferences.getInt("training_minute", 30),
    )
    private val current = MutableStateFlow(read())
    val state = current.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> current.value = read() }
    init { preferences.registerOnSharedPreferenceChangeListener(listener) }
    fun update(transform: (ComfortOptions) -> ComfortOptions) {
        val next = transform(current.value)
        require(next.weightHour in 0..23 && next.trainingHour in 0..23 && next.weightMinute in 0..59 && next.trainingMinute in 0..59)
        preferences.edit().putString("theme", next.theme.name).putBoolean("haptics", next.haptics)
            .putBoolean("keep_awake", next.keepAwake).putInt("rest_seconds", next.restSeconds.coerceIn(0, 600))
            .putBoolean("weight_reminder", next.weightReminder).putInt("weight_hour", next.weightHour).putInt("weight_minute", next.weightMinute)
            .putBoolean("training_reminder", next.trainingReminder).putInt("training_hour", next.trainingHour).putInt("training_minute", next.trainingMinute).apply()
        current.value = next
    }
}
