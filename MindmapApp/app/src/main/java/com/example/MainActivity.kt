package com.example.mindmap

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mindmap.ui.screens.MapListScreen
import com.example.mindmap.ui.screens.MindMapEditorScreen
import com.example.mindmap.viewmodel.MindMapViewModel
import com.example.mindmap.viewmodel.ScreenState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindMapTheme {
                MindMapApp()
            }
        }
    }
}

@Composable
fun MindMapApp(viewModel: MindMapViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Crossfade(targetState = uiState.screenState, label = "ScreenTransition") { screen ->
        when (screen) {
            is ScreenState.MapList -> {
                MapListScreen(
                    maps = uiState.mapList,
                    onCreateMap = { title -> viewModel.createNewMap(title) },
                    onOpenMap = { id -> viewModel.openMap(id) },
                    onRenameMap = { id, newTitle -> viewModel.renameMap(id, newTitle) },
                    onDeleteMap = { id -> viewModel.deleteMap(id) }
                )
            }
            is ScreenState.Editor -> {
                MindMapEditorScreen(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * Адаптивная тема Material 3 с поддержкой светлого/тёмного режимов и Dynamic Color (Android 12+).
 */
@Composable
fun MindMapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}