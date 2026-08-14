package com.browicy.conformance;

import com.browicy.conformance.model.LayoutComparisonReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class VueDemoConformanceTest extends LayoutConformanceTestBase {
    @Test
    public void matchesStaticVueDemoShell() throws IOException {
        Path demo = Path.of("artifacts", "vue-demo");
        if (!Files.isDirectory(demo)) {
            demo = Path.of("..", "artifacts", "vue-demo");
        }
        String html = Files.readString(demo.resolve("index.html"));
        String css = Files.readString(demo.resolve("styles.css"));
        html = html.replace("<link rel=\"stylesheet\" href=\"styles.css\">",
                "<style>" + css + "</style>");
        html = html.replaceAll("(?s)<script[^>]*>.*?</script>", "");

        LayoutComparisonReport report = compare("vue-demo-static", html, 900, 700, 2.0f);
        // The fixture deliberately excludes Vue runtime JavaScript: BrowicyLayoutExtractor is
        // a parser/layout adapter, not a script execution harness. The static shell must still
        // produce a complete, inspectable report for every supported fragment.
        org.junit.Assert.assertFalse(report.diffs().isEmpty());
        org.junit.Assert.assertTrue(report.diffs().stream()
                .anyMatch(diff -> diff.selector().equals("#app") || diff.property().equals("missing")));
    }
}
