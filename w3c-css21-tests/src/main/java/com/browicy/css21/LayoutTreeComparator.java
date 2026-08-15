package com.browicy.css21;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class LayoutTreeComparator {

    public static final double DEFAULT_TOLERANCE_PX = 0.5;

    private LayoutTreeComparator() {
    }

    public record StyleDiff(String property, String expected, String actual) {
        public boolean matches() {
            return Objects.equals(expected, actual);
        }
    }

    public record ElementDiff(
            String path,
            String tagName,
            String id,
            String className,
            ElementGeometrySnapshot.Rect expectedRect,
            ElementGeometrySnapshot.Rect actualRect,
            ElementGeometrySnapshot.Rect deltaRect,
            boolean rectMatched,
            Map<String, StyleDiff> styleDiffs,
            boolean presentInExpected,
            boolean presentInActual) {

        public boolean isMatch() {
            return presentInExpected && presentInActual && rectMatched && styleDiffs.isEmpty();
        }

        public double positionDelta() {
            if (deltaRect == null) {
                return 0;
            }
            return Math.max(Math.abs(deltaRect.x()), Math.abs(deltaRect.y()));
        }

        public double sizeDelta() {
            if (deltaRect == null) {
                return 0;
            }
            return Math.max(Math.abs(deltaRect.width()), Math.abs(deltaRect.height()));
        }
    }

    public record ComparisonResult(
            List<ElementDiff> elementDiffs,
            int totalExpected,
            int totalActual,
            int matchedElements,
            int mismatchedElements,
            int missingInActual,
            int extraInActual,
            double maxPositionDelta,
            double maxSizeDelta) {

        public boolean passed() {
            return mismatchedElements == 0 && missingInActual == 0 && extraInActual == 0;
        }
    }

    public static ComparisonResult compare(List<ElementGeometrySnapshot> expected,
                                           List<ElementGeometrySnapshot> actual) {
        return compare(expected, actual, DEFAULT_TOLERANCE_PX);
    }

    public static ComparisonResult compare(List<ElementGeometrySnapshot> expected,
                                           List<ElementGeometrySnapshot> actual,
                                           double tolerancePx) {
        Map<String, ElementGeometrySnapshot> actualByPath = new LinkedHashMap<>();
        for (ElementGeometrySnapshot item : actual) {
            actualByPath.put(item.path(), item);
        }

        List<ElementDiff> diffs = new ArrayList<>();
        int matched = 0;
        int mismatched = 0;
        int missing = 0;
        double maxPosDelta = 0;
        double maxSizeDelta = 0;

        for (ElementGeometrySnapshot exp : expected) {
            ElementGeometrySnapshot act = actualByPath.remove(exp.path());
            if (act == null) {
                missing++;
                diffs.add(new ElementDiff(
                        exp.path(), exp.tagName(), exp.id(), exp.className(),
                        exp.rect(), null, null, false, Collections.emptyMap(), true, false));
                continue;
            }

            boolean rectMatched = exp.rect().matches(act.rect(), tolerancePx);
            ElementGeometrySnapshot.Rect delta = exp.rect().deltaTo(act.rect());
            Map<String, StyleDiff> styleDiffs = compareStyles(exp.computedStyles(), act.computedStyles());

            ElementDiff elementDiff = new ElementDiff(
                    exp.path(), exp.tagName(), exp.id(), exp.className(),
                    exp.rect(), act.rect(), delta, rectMatched, styleDiffs, true, true);

            diffs.add(elementDiff);
            if (elementDiff.isMatch()) {
                matched++;
            } else {
                mismatched++;
                maxPosDelta = Math.max(maxPosDelta, elementDiff.positionDelta());
                maxSizeDelta = Math.max(maxSizeDelta, elementDiff.sizeDelta());
            }
        }

        int extra = actualByPath.size();
        for (ElementGeometrySnapshot act : actualByPath.values()) {
            diffs.add(new ElementDiff(
                    act.path(), act.tagName(), act.id(), act.className(),
                    null, act.rect(), null, false, Collections.emptyMap(), false, true));
        }

        return new ComparisonResult(
                List.copyOf(diffs), expected.size(), actual.size(),
                matched, mismatched, missing, extra, maxPosDelta, maxSizeDelta);
    }

    private static Map<String, StyleDiff> compareStyles(Map<String, String> expected,
                                                        Map<String, String> actual) {
        Map<String, StyleDiff> diffs = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String prop = entry.getKey();
            String expVal = entry.getValue();
            String actVal = actual.get(prop);
            if (actVal != null && !valuesMatch(prop, expVal, actVal)) {
                diffs.put(prop, new StyleDiff(prop, expVal, actVal));
            }
        }
        return Collections.unmodifiableMap(diffs);
    }

    private static boolean valuesMatch(String property, String expected, String actual) {
        if (Objects.equals(expected, actual)) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.endsWith("px") && actual.endsWith("px")) {
            try {
                double e = Double.parseDouble(expected.substring(0, expected.length() - 2).strip());
                double a = Double.parseDouble(actual.substring(0, actual.length() - 2).strip());
                return Math.abs(e - a) <= DEFAULT_TOLERANCE_PX;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public static String formatTable(ComparisonResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "Layout-Tree-Vergleich: %d Elemente, %d PASS, %d DIFF, %d Fehlt, %d Extra (Max dPos: %.1fpx, Max dSize: %.1fpx)\n",
                result.totalExpected(), result.matchedElements(), result.mismatchedElements(),
                result.missingInActual(), result.extraInActual(), result.maxPositionDelta(), result.maxSizeDelta()));
        sb.append("------------------------------------------------------------------------------------------------------------------------\n");
        sb.append(String.format(Locale.ROOT, "%-5s | %-32s | %-22s | %-22s | %-20s\n",
                "Status", "Element (Pfad)", "Chrome (x,y wxh)", "Browicy (x,y wxh)", "Delta (dx,dy dwxdh)"));
        sb.append("------------------------------------------------------------------------------------------------------------------------\n");

        for (ElementDiff diff : result.elementDiffs()) {
            String status = diff.isMatch() ? "PASS"
                    : !diff.presentInActual() ? "MISS"
                    : !diff.presentInExpected() ? "EXTRA" : "DIFF";

            String pathShort = shorten(diff.path(), 32);
            String expStr = diff.expectedRect() != null ? diff.expectedRect().toString() : "—";
            String actStr = diff.actualRect() != null ? diff.actualRect().toString() : "—";
            String deltaStr = diff.deltaRect() != null ? String.format(Locale.ROOT,
                    "(%+.1f, %+.1f) %+.1fx%+.1f",
                    diff.deltaRect().x(), diff.deltaRect().y(),
                    diff.deltaRect().width(), diff.deltaRect().height()) : "—";

            sb.append(String.format(Locale.ROOT, "%-6s | %-32s | %-22s | %-22s | %-20s\n",
                    status, pathShort, expStr, actStr, deltaStr));

            if (!diff.styleDiffs().isEmpty()) {
                for (StyleDiff sd : diff.styleDiffs().values()) {
                    sb.append(String.format(Locale.ROOT, "       -> Style-Diff [%s]: Chrome='%s', Browicy='%s'\n",
                            sd.property(), sd.expected(), sd.actual()));
                }
            }
        }
        sb.append("------------------------------------------------------------------------------------------------------------------------\n");
        return sb.toString();
    }

    public static String toJson(ComparisonResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"summary\": {\n");
        json.append("    \"passed\": ").append(result.passed()).append(",\n");
        json.append("    \"totalExpected\": ").append(result.totalExpected()).append(",\n");
        json.append("    \"totalActual\": ").append(result.totalActual()).append(",\n");
        json.append("    \"matchedElements\": ").append(result.matchedElements()).append(",\n");
        json.append("    \"mismatchedElements\": ").append(result.mismatchedElements()).append(",\n");
        json.append("    \"missingInActual\": ").append(result.missingInActual()).append(",\n");
        json.append("    \"extraInActual\": ").append(result.extraInActual()).append(",\n");
        json.append(String.format(Locale.ROOT, "    \"maxPositionDelta\": %.2f,\n", result.maxPositionDelta()));
        json.append(String.format(Locale.ROOT, "    \"maxSizeDelta\": %.2f\n", result.maxSizeDelta()));
        json.append("  },\n");
        json.append("  \"elements\": [\n");

        for (int i = 0; i < result.elementDiffs().size(); i++) {
            ElementDiff d = result.elementDiffs().get(i);
            json.append("    {\n");
            json.append("      \"path\": \"").append(jsonEscape(d.path())).append("\",\n");
            json.append("      \"tag\": \"").append(jsonEscape(d.tagName())).append("\",\n");
            json.append("      \"id\": \"").append(jsonEscape(d.id())).append("\",\n");
            json.append("      \"match\": ").append(d.isMatch()).append(",\n");
            if (d.expectedRect() != null) {
                json.append("      \"expectedRect\": {\"x\": ").append(d.expectedRect().x())
                        .append(", \"y\": ").append(d.expectedRect().y())
                        .append(", \"width\": ").append(d.expectedRect().width())
                        .append(", \"height\": ").append(d.expectedRect().height()).append("},\n");
            }
            if (d.actualRect() != null) {
                json.append("      \"actualRect\": {\"x\": ").append(d.actualRect().x())
                        .append(", \"y\": ").append(d.actualRect().y())
                        .append(", \"width\": ").append(d.actualRect().width())
                        .append(", \"height\": ").append(d.actualRect().height()).append("},\n");
            }
            if (d.deltaRect() != null) {
                json.append("      \"deltaRect\": {\"dx\": ").append(d.deltaRect().x())
                        .append(", \"dy\": ").append(d.deltaRect().y())
                        .append(", \"dWidth\": ").append(d.deltaRect().width())
                        .append(", \"dHeight\": ").append(d.deltaRect().height()).append("},\n");
            }
            json.append("      \"styleDiffs\": {");
            boolean firstStyle = true;
            for (StyleDiff sd : d.styleDiffs().values()) {
                if (!firstStyle) json.append(", ");
                firstStyle = false;
                json.append("\"").append(jsonEscape(sd.property())).append("\": {\"expected\": \"")
                        .append(jsonEscape(sd.expected())).append("\", \"actual\": \"")
                        .append(jsonEscape(sd.actual())).append("\"}");
            }
            json.append("}\n");
            json.append("    }").append(i < result.elementDiffs().size() - 1 ? ",\n" : "\n");
        }

        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return "..." + text.substring(text.length() - (max - 1));
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
