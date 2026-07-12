package com.browicy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.browicy.engine.dom.Document
import com.browicy.engine.dom.Element
import com.browicy.engine.dom.Node
import com.browicy.engine.dom.TextNode

/**
 * Rendert einen DOM-Baum der Browicy-Engine als einfache Textblöcke.
 * Ohne CSS-Unterstützung gibt es nur die "Browser-Default"-Darstellung:
 * Überschriften größer, Absätze normal.
 */
@Composable
fun DomView(document: Document, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        document.body?.let { RenderChildren(it) }
    }
}

/** Elemente, deren Inhalt nicht dargestellt wird. */
private val HIDDEN_TAGS = setOf("head", "title", "meta", "link", "script", "style")

/** Tags, die als eigener Block (eigene Zeile) gerendert werden. */
private val BLOCK_TAGS = setOf(
    "p", "div", "section", "article", "main", "header", "footer",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
)

@Composable
private fun RenderChildren(node: Node) {
    node.children.forEach { child ->
        when (child) {
            is TextNode -> if (!child.isBlank) {
                Text(collapseWhitespace(child.data))
            }
            is Element -> RenderElement(child)
        }
    }
}

@Composable
private fun RenderElement(element: Element) {
    if (element.tagName in HIDDEN_TAGS) return

    val containsBlocks = element.childElements.any { it.tagName in BLOCK_TAGS }
    if (containsBlocks) {
        // Container mit Block-Kindern: Kinder einzeln als Blöcke rendern
        RenderChildren(element)
        return
    }
    val text = collapseWhitespace(element.textContent)
    if (text.isNotBlank()) {
        Text(text = text, style = styleFor(element.tagName))
    }
}

@Composable
private fun styleFor(tagName: String): TextStyle = when (tagName) {
    "h1" -> MaterialTheme.typography.headlineLarge
    "h2" -> MaterialTheme.typography.headlineMedium
    "h3" -> MaterialTheme.typography.headlineSmall
    "h4", "h5", "h6" -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.bodyLarge
}

/** HTML-Whitespace-Verhalten: aufeinanderfolgende Leerzeichen/Umbrüche zu einem Leerzeichen zusammenfassen. */
private fun collapseWhitespace(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()
