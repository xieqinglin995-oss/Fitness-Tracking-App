package com.xieqinglin.fitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xieqinglin.fitness.ui.FitnessApp
import com.xieqinglin.fitness.ui.FitnessTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.xieqinglin.fitness.platform.ThemeChoice

class MainActivity : ComponentActivity() {
    val requestedAction = MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedAction.value = intent?.getStringExtra("open_action")
        setContent {
            val options by (application as FitnessApplication).comfort.state.collectAsStateWithLifecycle()
            val dark = when (options.theme) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.DARK -> true; ThemeChoice.LIGHT -> false }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(0xFF101623.toInt())
                        else SystemBarStyle.light(0xFFF3F5F9.toInt(), 0xFF101623.toInt()),
                )
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            FitnessTheme(options.theme) { FitnessApp(viewModel()) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedAction.value = intent.getStringExtra("open_action")
    }
}
