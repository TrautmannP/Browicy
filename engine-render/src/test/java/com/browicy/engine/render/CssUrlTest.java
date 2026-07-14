package com.browicy.engine.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CssUrlTest {

    @Test
    public void parsesOnlyOneSafeUrlToken() {
        assertEquals("images/a.png", CssUrl.parseSingle("url('images/a.png')"));
        assertEquals("https://example.test/a.png",
                CssUrl.parseSingle(" URL(https://example.test/a.png) "));
        assertNull(CssUrl.parseSingle("url(javascript:alert(1))"));
        assertNull(CssUrl.parseSingle("url(data:image/png;base64,AAAA)"));
        assertNull(CssUrl.parseSingle("url(a.png) trailing"));
    }

    @Test
    public void rewritesTokensButNotStringsOrComments() {
        String css = "a{content:'url(fake.png)';background:url(real.png)}"
                + "/* url(comment.png) */";

        assertEquals("a{content:'url(fake.png)';background:url(\"/base/real.png\")}"
                        + "/* url(comment.png) */",
                CssUrl.rewrite(css, source -> "/base/" + source));
    }

    @Test
    public void findsSafeUrlTokensForFontSources() {
        var tokens = CssUrl.tokens("local('System'), url(font.woff2) format('woff2'), "
                + "url(javascript:bad), url(backup.ttf)");

        assertEquals(2, tokens.size());
        assertEquals("font.woff2", tokens.get(0).source());
        assertEquals("backup.ttf", tokens.get(1).source());
    }
}
