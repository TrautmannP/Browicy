package com.browicy.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class BrowserState {
    val tabs: SnapshotStateList<BrowserTab> = mutableStateListOf(BrowserTab())
    val selectedTabId = mutableStateOf(tabs.first().id)

    val selectedTab: BrowserTab
        get() = tabs.firstOrNull { it.id == selectedTabId.value } ?: tabs.first()

    fun selectTab(id: String) {
        selectedTabId.value = id
    }

    fun addTab() {
        val tab = BrowserTab()
        tabs.add(tab)
        selectedTabId.value = tab.id
    }

    fun removeTab(id: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            tabs.add(BrowserTab())
        }
        if (selectedTabId.value == id) {
            val newIndex = (index - 1).coerceAtLeast(0)
            selectedTabId.value = tabs[newIndex.coerceAtMost(tabs.lastIndex)].id
        }
    }

    fun updateUrl(id: String, url: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index >= 0) {
            tabs[index] = tabs[index].copy(url = url)
        }
    }

    fun updateTitle(id: String, title: String) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index >= 0) {
            tabs[index] = tabs[index].copy(title = title)
        }
    }
}
