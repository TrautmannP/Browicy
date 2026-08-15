package com.browicy.css21;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ElementGeometrySnapshot(
        String path,
        String tagName,
        String id,
        String className,
        Rect rect,
        Map<String, String> computedStyles) {

    public ElementGeometrySnapshot {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(tagName, "tagName");
        id = id == null ? "" : id;
        className = className == null ? "" : className;
        rect = rect == null ? Rect.ZERO : rect;
        computedStyles = Collections.unmodifiableMap(new LinkedHashMap<>(computedStyles));
    }

    public record Rect(double x, double y, double width, double height) {
        public static final Rect ZERO = new Rect(0, 0, 0, 0);

        public double right() {
            return x + width;
        }

        public double bottom() {
            return y + height;
        }

        public boolean matches(Rect other, double tolerance) {
            if (other == null) {
                return false;
            }
            return Math.abs(x - other.x) <= tolerance
                    && Math.abs(y - other.y) <= tolerance
                    && Math.abs(width - other.width) <= tolerance
                    && Math.abs(height - other.height) <= tolerance;
        }

        public Rect deltaTo(Rect other) {
            if (other == null) {
                return new Rect(x, y, width, height);
            }
            return new Rect(other.x - x, other.y - y, other.width - width, other.height - height);
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "(%.1f, %.1f) %.1fx%.1f", x, y, width, height);
        }
    }
}
