package com.browicy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.WindowScope
import com.browicy.engine.BrowicyEngine
import com.browicy.model.BrowserState

@Composable
fun rememberBrowserState(): BrowserState = remember { BrowserState() }

@Composable
fun WindowScope.BrowserApp(
    state: BrowserState = rememberBrowserState(),
    onMinimize: () -> Unit = {},
    onMaximizeToggle: () -> Unit = {},
    onClose: () -> Unit = {},
    isMaximized: Boolean = false,
) {
    val engine = remember { BrowicyEngine() }
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserTitleBar(
                onMinimize = onMinimize,
                onMaximizeToggle = onMaximizeToggle,
                onClose = onClose,
                isMaximized = isMaximized,
            )
            BrowserTabBar(
                tabs = state.tabs,
                selectedTabId = state.selectedTabId.value,
                onSelect = state::selectTab,
                onClose = state::removeTab,
                onAdd = state::addTab,
            )
            BrowserToolbar(
                tab = state.selectedTab,
                onNavigate = { url ->
                    val target = url.trim()
                    if (target.isNotEmpty()) {
                        state.updateUrl(state.selectedTab.id, target)
                    }
                },
            )
            BrowserContent(
                tab = state.selectedTab,
                engine = engine,
                onTitleChange = { title -> state.updateTitle(state.selectedTab.id, title) },
            )
        }
    }
}
