package com.browicy.engine.css;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class CssStyleSheetCachingTest {

    @Test
    public void parsedRulesReturnsTheSameImmutableInstanceAcrossCalls() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        CssStyleSheet sheet = registry.register(0, "p { color: red } h1 { color: blue }");

        List<CssRule> first = sheet.parsedRules();
        List<CssRule> second = sheet.parsedRules();

        assertSame("parsedRules() muss die gecachte Instanz zurückgeben", first, second);
        assertEquals(2, first.size());
        assertEquals("p", first.getFirst().selector().toString());
        assertThrows("Die gecachte Liste muss unveränderlich sein",
                UnsupportedOperationException.class, () -> first.clear());
    }

    @Test
    public void registryRulesReturnsTheSameInstanceUntilASheetChanges() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        registry.register(0, "p { color: red }");

        List<CssRule> first = registry.rules();
        assertSame("registry.rules() muss die gecachte flache Liste zurückgeben",
                first, registry.rules());

        registry.register(1, "h1 { color: green }");
        List<CssRule> second = registry.rules();
        assertNotSame("Neue Registrierung muss den flachen Cache invalidieren", first, second);
        assertEquals(2, second.size());
        assertEquals("h1", second.get(1).selector().toString());
    }

    @Test
    public void insertRuleAndDeleteRuleRefreshSheetAndRegistryCaches() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        CssStyleSheet sheet = registry.register(0, "p { color: red }");
        List<CssRule> before = registry.rules();
        assertEquals(1, before.size());

        sheet.insertRule("h1 { color: blue }", 1);

        List<CssRule> afterInsert = registry.rules();
        assertNotSame("insertRule muss den Registry-Cache invalidieren", before, afterInsert);
        assertEquals(2, afterInsert.size());
        assertEquals("h1", afterInsert.get(1).selector().toString());
        assertEquals(2, sheet.parsedRules().size());

        sheet.deleteRule(0);

        List<CssRule> afterDelete = registry.rules();
        assertNotSame("deleteRule muss den Registry-Cache invalidieren",
                afterInsert, afterDelete);
        assertEquals(1, afterDelete.size());
        assertEquals("h1", afterDelete.getFirst().selector().toString());
        assertEquals(1, sheet.parsedRules().size());
    }

    @Test
    public void replacingARegisteredSourceRefreshesTheCachedRules() {
        StyleSheetRegistry registry = new StyleSheetRegistry();
        registry.register(0, "p { color: red }");
        assertEquals("red", registry.rules().getFirst().declarations().get("color"));

        registry.register(0, "p { color: green }");

        List<CssRule> refreshed = registry.rules();
        assertEquals(1, refreshed.size());
        assertEquals("green", refreshed.getFirst().declarations().get("color"));
    }
}
