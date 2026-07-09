package com.browicy.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browicy.model.BrowserTab

@Composable
fun BrowserTabBar(
    tabs: List<BrowserTab>,
    selectedTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)

    Row(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(tab.id) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.title, maxLines = 1)
                            IconButton(
                                onClick = { onClose(tab.id) },
                                modifier = Modifier.size(20.dp).padding(start = 4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Tab schließen",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.align(Alignment.CenterVertically)) {
            Icon(Icons.Filled.Add, contentDescription = "Neuer Tab")
        }
    }
}
