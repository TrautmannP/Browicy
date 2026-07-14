package com.browicy.engine;

import com.browicy.engine.net.BinaryResource;
import com.browicy.engine.net.NetworkResourceType;
import java.net.URI;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageResourceCoordinatorSecurityTest {

    @Test
    public void requiresCorsForCrossOriginFonts() {
        URI page = URI.create("https://page.example/index.html");
        URI font = URI.create("https://fonts.example/font.ttf");

        assertFalse(PageResourceCoordinator.corsAllows(page, resource(font, null)));
        assertTrue(PageResourceCoordinator.corsAllows(page, resource(font, "*")));
        assertTrue(PageResourceCoordinator.corsAllows(
                page, resource(font, "https://page.example")));
        assertFalse(PageResourceCoordinator.corsAllows(
                page, resource(font, "https://other.example")));
    }

    @Test
    public void sameOriginIncludesEffectiveDefaultPort() {
        URI page = URI.create("https://page.example:443/index.html");
        assertTrue(PageResourceCoordinator.corsAllows(page,
                resource(URI.create("https://page.example/font.ttf"), null)));
    }

    private static BinaryResource resource(URI uri, String allowOrigin) {
        return new BinaryResource(uri, 200, new byte[0], NetworkResourceType.FONT, allowOrigin);
    }
}
