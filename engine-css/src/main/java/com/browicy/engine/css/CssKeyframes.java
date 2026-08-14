package com.browicy.engine.css;

import java.util.List;
import java.util.Map;

/**
 * A parsed {@code @keyframes} rule: the animation name and its keyframe
 * blocks (from/to/percentage selectors with declarations).
 */
public record CssKeyframes(String name, List<Block> blocks) {

    public record Block(String selector, Map<String, String> declarations) {
    }
}
