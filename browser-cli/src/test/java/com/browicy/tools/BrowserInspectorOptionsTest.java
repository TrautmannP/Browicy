package com.browicy.tools;

import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BrowserInspectorOptionsTest {

    @Test
    public void parsesScreenshotOptions() {
        BrowserInspector.Options options = BrowserInspector.Options.parse(new String[]{
                "https://example.com",
                "--screenshot", "artifacts/page.png",
                "--viewport", "1440x900",
                "--full-page"
        });

        assertEquals(Path.of("artifacts/page.png"), options.screenshot());
        assertEquals(1440, options.viewportWidth());
        assertEquals(900, options.viewportHeight());
        assertTrue(options.fullPage());
    }

    @Test
    public void usesStableViewportDefaults() {
        BrowserInspector.Options options = BrowserInspector.Options.parse(
                new String[]{"https://example.com"});

        assertEquals(1280, options.viewportWidth());
        assertEquals(720, options.viewportHeight());
        assertFalse(options.fullPage());
    }

    @Test
    public void rejectsInvalidViewportAndOrphanedFullPageOption() {
        assertThrows(IllegalArgumentException.class, () -> BrowserInspector.Options.parse(
                new String[]{"https://example.com", "--viewport", "wide"}));
        assertThrows(IllegalArgumentException.class, () -> BrowserInspector.Options.parse(
                new String[]{"https://example.com", "--full-page"}));
        assertThrows(IllegalArgumentException.class, () -> BrowserInspector.Options.parse(
                new String[]{"https://example.com", "--output", "result.png",
                        "--screenshot", "result.png"}));
    }
}
