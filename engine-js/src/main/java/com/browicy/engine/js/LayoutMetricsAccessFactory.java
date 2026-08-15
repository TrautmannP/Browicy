package com.browicy.engine.js;

import com.browicy.engine.css.StyleSheetRegistry;

/**
 * Fabrik für {@link LayoutMetricsAccess}-Implementierungen: erhält die
 * {@link StyleSheetRegistry} der laufenden Seite (inklusive per JS
 * injizierter Stylesheets), damit ein Forced Reflow gegen das aktuelle
 * Dokument möglich ist. Viewport-Maße schließt der Aufrufer ein.
 */
@FunctionalInterface
public interface LayoutMetricsAccessFactory {

    LayoutMetricsAccess create(StyleSheetRegistry styleSheets);
}
