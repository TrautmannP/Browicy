package com.browicy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browicy.engine.BrowicyEngine
import com.browicy.model.BrowserTab

@Composable
fun BrowserContent(
    tab: BrowserTab,
    engine: BrowicyEngine,
    onTitleChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (tab.url == "about:blank") {
            EmptyTabContent(tab)
        } else {
            val document = remember(tab.url) { engine.loadPage(tab.url) }
            LaunchedEffect(document) {
                val title = document.title
                if (title.isNotBlank()) {
                    onTitleChange(title)
                }
            }
            DomView(document)
        }
    }
}

@Composable
private fun EmptyTabContent(tab: BrowserTab) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Neuer Tab",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Gib eine URL ein und bestätige mit Enter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Tab: ${tab.title}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
