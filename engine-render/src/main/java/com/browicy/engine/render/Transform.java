package com.browicy.engine.render;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A CSS transform list plus transform-origin. Each operation is applied in
 * list order (left to right, matching browser composition). Lengths resolve
 * against the box's own size for percentages, root font for rem and viewport
 * dimensions for vw/vh at matrix construction time.
 */
public record Transform(List<Operation> operations, RenderOffset originX, RenderOffset originY) {

    public static final Transform NONE = new Transform(List.of(),
            new RenderOffset(50, RenderOffset.Unit.PERCENT),
            new RenderOffset(50, RenderOffset.Unit.PERCENT));

    private static final Pattern FUNCTION = Pattern.compile("([a-zA-Z]+)\\(([^)]*)\\)");
    private static final Pattern OFFSET = Pattern.compile(
            "([-+]?[0-9]*\\.?[0-9]+)(%|px|rem|vw|vh)?");
    private static final Pattern NUMBER = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");

    public enum Kind {
        TRANSLATE_X, TRANSLATE_Y, TRANSLATE, ROTATE, SCALE_X, SCALE_Y, SCALE, MATRIX
    }

    public record Operation(Kind kind, RenderOffset x, RenderOffset y,
                            float angleRad, float scaleX, float scaleY,
                            double[] matrix) {
    }

    public boolean isIdentity() {
        return operations.isEmpty();
    }

    public Transform withOrigin(RenderOffset newOriginX, RenderOffset newOriginY) {
        return new Transform(operations, newOriginX, newOriginY);
    }

    public static Transform parse(String value, float rootFontSizePx) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.equals("none")) {
            return NONE;
        }
        List<Operation> ops = new ArrayList<>();
        Matcher matcher = FUNCTION.matcher(value);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() != lastEnd
                    && !value.substring(lastEnd, matcher.start()).isBlank()) {
                return null;
            }
            Operation op = parseFunction(matcher.group(1), matcher.group(2));
            if (op == null) {
                return null;
            }
            ops.add(op);
            lastEnd = matcher.end();
        }
        if (ops.isEmpty() || lastEnd != value.length()) {
            return null;
        }
        return new Transform(ops,
                new RenderOffset(50, RenderOffset.Unit.PERCENT),
                new RenderOffset(50, RenderOffset.Unit.PERCENT));
    }

    private static Operation parseFunction(String name, String args) {
        List<String> parts = splitComma(args);
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "translate" -> {
                if (parts.size() < 1 || parts.size() > 2) {
                    return null;
                }
                RenderOffset x = parseOffset(parts.get(0));
                RenderOffset y = parts.size() == 2
                        ? parseOffset(parts.get(1))
                        : new RenderOffset(0, RenderOffset.Unit.PX);
                if (x == null || y == null) {
                    return null;
                }
                return new Operation(Kind.TRANSLATE, x, y, 0, 1, 1, null);
            }
            case "translatex", "translatey" -> {
                if (parts.size() != 1) {
                    return null;
                }
                RenderOffset value = parseOffset(parts.get(0));
                if (value == null) {
                    return null;
                }
                return name.equalsIgnoreCase("translateX")
                        ? new Operation(Kind.TRANSLATE_X, value, null, 0, 1, 1, null)
                        : new Operation(Kind.TRANSLATE_Y, null, value, 0, 1, 1, null);
            }
            case "rotate" -> {
                if (parts.size() != 1) {
                    return null;
                }
                Float radians = parseAngle(parts.get(0));
                if (radians == null) {
                    return null;
                }
                return new Operation(Kind.ROTATE, null, null, radians, 1, 1, null);
            }
            case "scale" -> {
                if (parts.size() < 1 || parts.size() > 2) {
                    return null;
                }
                Float sx = parseNumber(parts.get(0));
                Float sy = parts.size() == 2 ? parseNumber(parts.get(1)) : sx;
                if (sx == null || sy == null) {
                    return null;
                }
                return new Operation(Kind.SCALE, null, null, 0, sx, sy, null);
            }
            case "scalex", "scaley" -> {
                if (parts.size() != 1) {
                    return null;
                }
                Float value = parseNumber(parts.get(0));
                if (value == null) {
                    return null;
                }
                return name.equalsIgnoreCase("scaleX")
                        ? new Operation(Kind.SCALE_X, null, null, 0, value, 1, null)
                        : new Operation(Kind.SCALE_Y, null, null, 0, 1, value, null);
            }
            case "matrix" -> {
                if (parts.size() != 6) {
                    return null;
                }
                double[] values = new double[6];
                for (int index = 0; index < 6; index++) {
                    if (!NUMBER.matcher(parts.get(index)).matches()) {
                        return null;
                    }
                    values[index] = Double.parseDouble(parts.get(index));
                }
                return new Operation(Kind.MATRIX, null, null, 0, 1, 1, values);
            }
            default -> {
                return null;
            }
        }
    }

    private static List<String> splitComma(String args) {
        List<String> parts = new ArrayList<>();
        for (String part : args.split(",", -1)) {
            parts.add(part.trim());
        }
        return parts;
    }

    private static RenderOffset parseOffset(String value) {
        Matcher matcher = OFFSET.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        float number = Float.parseFloat(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null && number != 0) {
            return null; // Einheitslose Längen sind nur als 0 gültig.
        }
        RenderOffset.Unit offsetUnit = switch (unit) {
            case "%" -> RenderOffset.Unit.PERCENT;
            case "rem" -> RenderOffset.Unit.REM;
            case "vw" -> RenderOffset.Unit.VW;
            case "vh" -> RenderOffset.Unit.VH;
            default -> RenderOffset.Unit.PX;
        };
        return new RenderOffset(number, offsetUnit);
    }

    private static Float parseAngle(String value) {
        Matcher matcher = NUMBER.matcher(value);
        if (!matcher.lookingAt()) {
            return null;
        }
        float number = Float.parseFloat(matcher.group());
        String unit = value.substring(matcher.end());
        return switch (unit) {
            case "deg" -> (float) Math.toRadians(number);
            case "turn" -> number * 2 * (float) Math.PI;
            case "grad" -> (float) Math.toRadians(number * 0.9f);
            case "rad", "" -> number;
            default -> null;
        };
    }

    private static Float parseNumber(String value) {
        if (!NUMBER.matcher(value).matches()) {
            return null;
        }
        return Float.parseFloat(value);
    }

    /**
     * Builds the affine transform about the box origin, resolving percentages
     * against the box's own width/height.
     */
    public AffineTransform matrix(float boxX, float boxY, float ownWidth, float ownHeight,
                                  float rootFontSizePx, float viewportWidth, float viewportHeight) {
        float originPxX = boxX + originX().resolve(ownWidth, rootFontSizePx,
                viewportWidth, viewportHeight);
        float originPxY = boxY + originY().resolve(ownHeight, rootFontSizePx,
                viewportWidth, viewportHeight);
        AffineTransform matrix = new AffineTransform();
        matrix.translate(originPxX, originPxY);
        for (Operation op : operations) {
            switch (op.kind()) {
                case TRANSLATE -> {
                    matrix.translate(op.x().resolve(ownWidth, rootFontSizePx,
                                    viewportWidth, viewportHeight),
                            op.y().resolve(ownHeight, rootFontSizePx,
                                    viewportWidth, viewportHeight));
                }
                case TRANSLATE_X -> matrix.translate(op.x().resolve(ownWidth,
                        rootFontSizePx, viewportWidth, viewportHeight), 0);
                case TRANSLATE_Y -> matrix.translate(0, op.y().resolve(ownHeight,
                        rootFontSizePx, viewportWidth, viewportHeight));
                case ROTATE -> matrix.rotate(op.angleRad());
                case SCALE, SCALE_X, SCALE_Y -> matrix.scale(op.scaleX(), op.scaleY());
                case MATRIX -> matrix.concatenate(new AffineTransform(op.matrix()[0],
                        op.matrix()[1], op.matrix()[2], op.matrix()[3],
                        op.matrix()[4], op.matrix()[5]));
            }
        }
        matrix.translate(-originPxX, -originPxY);
        return matrix;
    }
}
