package com.browicy.css21;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * JUnit-Gate über die W3C CSS2.1-Suite: ein parametrisierter Test pro
 * Suite-Test, der das Chrome-Sollbild mit der Browicy-Darstellung vergleicht.
 *
 * <p>Der komplette Harness-Lauf passiert einmal (statischer Initialisierer)
 * wie beim Acid3-Harness. Auswahl und Referenz-Neuerzeugung steuert man über
 * System-Properties, siehe {@link W3cCss21Harness}.</p>
 *
 * <pre>
 *   mvn -Pw3c-css21 -pl w3c-css21-tests -am test \
 *       -Dbrowicy.refreshReferences=true \
 *       -Dbrowicy.tests='abspos/.*'
 * </pre>
 */
@RunWith(Parameterized.class)
public class W3cCss21Test {

    private static final W3cCss21Harness.RunResult RESULTS = W3cCss21Harness.run();

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> tests() {
        List<Object[]> rows = new ArrayList<>();
        for (W3cCss21Harness.TestResult result : RESULTS.tests()) {
            rows.add(new Object[]{result.relativePath()});
        }
        return rows;
    }

    private final String relativePath;

    public W3cCss21Test(String relativePath) {
        this.relativePath = relativePath;
    }

    @Test
    public void matchesChrome() {
        W3cCss21Harness.TestResult result = RESULTS.tests().stream()
                .filter(test -> test.relativePath().equals(relativePath))
                .findFirst()
                .orElseThrow();
        org.junit.Assert.assertTrue(format(result), result.passed());
    }

    private static String format(W3cCss21Harness.TestResult result) {
        String artifactPath = result.relativePath().startsWith("<")
                ? "" : "\nArtefakte: " + RESULTS.outputDirectory().resolve(
                        "comparisons/" + result.relativePath().replace('/', '_'));
        return result.relativePath() + ": " + result.status() + " – " + result.message()
                + " (diffRatio=" + result.diffRatio() + ")" + artifactPath;
    }
}
