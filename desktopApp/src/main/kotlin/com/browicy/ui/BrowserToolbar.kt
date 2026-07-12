package com.browicy.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.browicy.model.BrowserTab

@Composable
fun BrowserToolbar(
    tab: BrowserTab,
    onNavigate: (String) -> Unit,
) {
    // Eingabe wird lokal gehalten und erst mit Enter/Go als Navigation committet
    var input by remember(tab.id, tab.url) {
        mutableStateOf(if (tab.url == "about:blank") "" else tab.url)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { /* TODO: Zurück */ }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { /* TODO: Vorwärts */ }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Vorwärts",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { onNavigate(input) }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Neuladen",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text("URL eingeben") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onNavigate(input) }),
            modifier = Modifier.weight(1f).padding(start = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
