package com.browicy.engine.html;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HtmlEntities {

    private static final String[] NAMED_NAMES = {
            "amp", "lt", "gt", "quot", "apos", "ndash", "mdash", "hellip",
            "lsquo", "rsquo", "ldquo", "rdquo", "larr", "uarr", "rarr", "darr",
            "copy", "reg", "trade", "sect", "para", "middot", "bull", "deg",
            "plusmn", "times", "divide", "euro", "pound", "yen", "cent", "szlig",
            "auml", "ouml", "uuml", "Auml", "Ouml", "Uuml", "nbsp"
    };

    private static final String[] NAMED_VALUES = {
            "&", "<", ">", "\"", "'", "–", "—", "…",
            "‘", "’", "“", "”", "←", "↑", "→", "↓",
            "©", "®", "™", "§", "¶", "·", "•", "°",
            "±", "×", "÷", "€", "£", "¥", "¢", "ß",
            "ä", "ö", "ü", "Ä", "Ö", "Ü", "\u00A0"
    };

    static String decode(String input) {
        int amp = input.indexOf('&');
        if (amp < 0) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length());
        int pos = 0;
        while (amp >= 0) {
            sb.append(input, pos, amp);
            int semicolon = input.indexOf(';', amp + 1);
            String replacement = null;
            if (semicolon > amp && semicolon - amp <= 10) {
                replacement = resolve(input, amp + 1, semicolon);
            }
            if (replacement != null) {
                sb.append(replacement);
                pos = semicolon + 1;
            } else {
                sb.append('&');
                pos = amp + 1;
            }
            amp = input.indexOf('&', pos);
        }
        sb.append(input, pos, input.length());
        return sb.toString();
    }

    private static String resolve(String input, int start, int end) {
        if (input.charAt(start) == '#') {
            if (end - start > 2) {
                char radixMarker = input.charAt(start + 1);
                if (radixMarker == 'x' || radixMarker == 'X') {
                    return decodeNumeric(input, start + 2, end, 16);
                }
            }
            if (end - start > 1) {
                return decodeNumeric(input, start + 1, end, 10);
            }
            return null;
        }
        for (int index = 0; index < NAMED_NAMES.length; index++) {
            String name = NAMED_NAMES[index];
            if (name.length() == end - start
                    && input.regionMatches(start, name, 0, name.length())) {
                return NAMED_VALUES[index];
            }
        }
        return null;
    }

    private static String decodeNumeric(String input, int start, int end, int radix) {
        if (start >= end) {
            return null;
        }
        int codePoint = 0;
        for (int index = start; index < end; index++) {
            int digit = Character.digit(input.charAt(index), radix);
            if (digit < 0 || codePoint > (Integer.MAX_VALUE - digit) / radix) {
                return null;
            }
            codePoint = codePoint * radix + digit;
        }
        if (!Character.isValidCodePoint(codePoint)) {
            return null;
        }
        return new String(Character.toChars(codePoint));
    }
}
