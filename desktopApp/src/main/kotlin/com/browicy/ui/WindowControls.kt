package com.browicy.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WindowControls(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onMaximizeToggle: () -> Unit,
    onClose: () -> Unit,
) {
    Row {
        IconButton(onClick = onMinimize, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Minimieren", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onMaximizeToggle, modifier = Modifier.size(40.dp)) {
            Icon(
                if (isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
                contentDescription = "Maximieren",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Preview
@Composable
private fun WindowControlsPreview() {
    MaterialTheme {
        WindowControls(
            isMaximized = false,
            onMinimize = {},
            onMaximizeToggle = {},
            onClose = {},
        )
    }
}
