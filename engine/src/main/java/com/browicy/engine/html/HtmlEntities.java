package com.browicy.engine.html;

import java.util.Map;

/**
 * Dekodiert die gebräuchlichsten HTML-Entities. Bewusst kein vollständiges
 * Named-Character-Reference-Verzeichnis der Spezifikation — für den Anfang
 * genügen die häufigsten benannten sowie numerische Referenzen.
 */
final class HtmlEntities {

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"),
            Map.entry("lt", "<"),
            Map.entry("gt", ">"),
            Map.entry("quot", "\""),
            Map.entry("apos", "'"),
            Map.entry("ndash", "–"),
            Map.entry("mdash", "—"),
            Map.entry("hellip", "…"),
            Map.entry("lsquo", "‘"),
            Map.entry("rsquo", "’"),
            Map.entry("ldquo", "“"),
            Map.entry("rdquo", "”"),
            Map.entry("larr", "←"),
            Map.entry("uarr", "↑"),
            Map.entry("rarr", "→"),
            Map.entry("darr", "↓"),
            Map.entry("copy", "©"),
            Map.entry("reg", "®"),
            Map.entry("trade", "™"),
            Map.entry("sect", "§"),
            Map.entry("para", "¶"),
            Map.entry("middot", "·"),
            Map.entry("bull", "•"),
            Map.entry("deg", "°"),
            Map.entry("plusmn", "±"),
            Map.entry("times", "×"),
            Map.entry("divide", "÷"),
            Map.entry("euro", "€"),
            Map.entry("pound", "£"),
            Map.entry("yen", "¥"),
            Map.entry("cent", "¢"),
            Map.entry("szlig", "ß"),
            Map.entry("auml", "ä"),
            Map.entry("ouml", "ö"),
            Map.entry("uuml", "ü"),
            Map.entry("Auml", "Ä"),
            Map.entry("Ouml", "Ö"),
            Map.entry("Uuml", "Ü"),
            Map.entry("nbsp", " ")
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
