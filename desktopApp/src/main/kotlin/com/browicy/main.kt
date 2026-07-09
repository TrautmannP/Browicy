package com.browicy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.browicy.ui.BrowserApp

fun main() = application {
    val state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "browicy",
        undecorated = true,
        state = state,
    ) {
        var isMaximized by remember { mutableStateOf(window.placement == WindowPlacement.Maximized) }

        BrowserApp(
            onMinimize = { window.isMinimized = true },
            onMaximizeToggle = {
                window.placement = if (window.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
                isMaximized = window.placement == WindowPlacement.Maximized
            },
            onClose = { exitApplication() },
            isMaximized = isMaximized,
        )
    }
}
