package com.browicy.engine.html;

import java.util.Map;

/**
 * Dekodiert die gebräuchlichsten HTML-Entities. Bewusst kein vollständiges
 * Named-Character-Reference-Verzeichnis der Spezifikation — für den Anfang
 * genügen die häufigsten benannten sowie numerische Referenzen.
 */
final class HtmlEntities {

    private static final Map<String, String> NAMED = Map.of(
            "amp", "&",
            "lt", "<",
            "gt", ">",
            "quot", "\"",
            "apos", "'",
            "nbsp", " "
    );

    private HtmlEntities() {
    }

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
                replacement = resolve(input.substring(amp + 1, semicolon));
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

    private static String resolve(String name) {
        if (name.startsWith("#x") || name.startsWith("#X")) {
            return decodeNumeric(name.substring(2), 16);
        }
        if (name.startsWith("#")) {
            return decodeNumeric(name.substring(1), 10);
        }
        return NAMED.get(name);
    }

    private static String decodeNumeric(String digits, int radix) {
        try {
            int codePoint = Integer.parseInt(digits, radix);
            if (Character.isValidCodePoint(codePoint)) {
                return new String(Character.toChars(codePoint));
            }
        } catch (NumberFormatException ignored) {
            // ungültige Referenz: unverändert lassen
        }
        return null;
    }
}
