package com.browicy.engine.html;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class HtmlTokenizer {

    private final String input;
    private int pos;

    public List<HtmlToken> tokenize() {
        List<HtmlToken> tokens = new ArrayList<>();
        tokenize(tokens::add);
        return tokens;
    }

    public void tokenize(Consumer<HtmlToken> sink) {
        while (pos < input.length()) {
            if (peek() == '<') {
                HtmlToken token = readMarkup();
                if (token != null) {
                    sink.accept(token);
                    if (token.type() == HtmlToken.Type.START_TAG
                            && !token.selfClosing()
                            && isRawText(token.name())) {
                        readRawText(token.name(), sink);
                    }
                }
            } else {
                sink.accept(HtmlToken.text(readText()));
            }
        }
    }

    private static boolean isRawText(String tagName) {
        return tagName.equals("script") || tagName.equals("style");
    }

    private HtmlToken readMarkup() {
        if (lookingAt("<!--")) {
            return readComment();
        }
        if (lookingAt("<!")) {
            return readDoctype();
        }
        if (lookingAt("</")) {
            return readEndTag();
        }
        if (pos + 1 < input.length() && Character.isLetter(input.charAt(pos + 1))) {
            return readStartTag();
        }
        pos++;
        return HtmlToken.text("<");
    }

    private HtmlToken readComment() {
        pos += 4;
        int end = input.indexOf("-->", pos);
        if (end < 0) {
            end = input.length();
        }
        String data = input.substring(pos, end);
        pos = Math.min(end + 3, input.length());
        return HtmlToken.comment(data);
    }

    private HtmlToken readDoctype() {
        pos += 2;
        int end = input.indexOf('>', pos);
        if (end < 0) {
            end = input.length();
        }
        String data = input.substring(pos, end);
        pos = Math.min(end + 1, input.length());
        return HtmlToken.doctype(data);
    }

    private HtmlToken readEndTag() {
        pos += 2;
        String name = readTagName();
        skipUntilAfter('>');
        return HtmlToken.endTag(name);
    }

    private HtmlToken readStartTag() {
        pos++;
        String name = readTagName();
        Map<String, String> attributes = null;
        boolean selfClosing = false;

        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) {
                break;
            }
            char c = peek();
            if (c == '>') {
                pos++;
                break;
            }
            if (c == '/') {
                pos++;
                if (pos < input.length() && peek() == '>') {
                    pos++;
                    selfClosing = true;
                    break;
                }
                continue;
            }
            if (attributes == null) {
                attributes = new LinkedHashMap<>();
            }
            readAttribute(attributes);
        }
        return HtmlToken.startTag(name, attributes == null ? Map.of() : attributes, selfClosing);
    }

    private void readAttribute(Map<String, String> attributes) {
        int start = pos;
        while (pos < input.length() && isAttributeNameChar(peek())) {
            pos++;
        }
        if (pos == start) {
            pos++;
            return;
        }
        String name = input.substring(start, pos).toLowerCase();
        skipWhitespace();

        String value = "";
        if (pos < input.length() && peek() == '=') {
            pos++;
            skipWhitespace();
            value = readAttributeValue();
        }
        attributes.putIfAbsent(name, HtmlEntities.decode(value));
    }

    private String readAttributeValue() {
        if (pos >= input.length()) {
            return "";
        }
        char quote = peek();
        if (quote == '"' || quote == '\'') {
            pos++;
            int end = input.indexOf(quote, pos);
            if (end < 0) {
                end = input.length();
            }
            String value = input.substring(pos, end);
            pos = Math.min(end + 1, input.length());
            return value;
        }
        int start = pos;
        while (pos < input.length() && !Character.isWhitespace(peek()) && peek() != '>') {
            pos++;
        }
        return input.substring(start, pos);
    }

    private String readTagName() {
        int start = pos;
        while (pos < input.length() && isTagNameChar(peek())) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private String readText() {
        int start = pos;
        int end = input.indexOf('<', start);
        if (end < 0) {
            end = input.length();
        }
        String raw = input.substring(start, end);
        pos = end;
        int amp = input.indexOf('&', start);
        if (amp < 0 || amp >= end) {
            return raw;
        }
        return HtmlEntities.decode(raw);
    }

    private void readRawText(String tagName, Consumer<HtmlToken> sink) {
        String closing = "</" + tagName;
        int end = indexOfIgnoreCase(closing);
        if (end < 0) {
            end = input.length();
        }
        if (end > pos) {
            sink.accept(HtmlToken.text(input.substring(pos, end)));
        }
        pos = end;
        if (pos < input.length()) {
            sink.accept(readEndTag());
        }
    }

    private int indexOfIgnoreCase(String needle) {
        int max = input.length() - needle.length();
        for (int index = pos; index <= max; index++) {
            if (input.regionMatches(true, index, needle, 0, needle.length())) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isTagNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-';
    }

    private static boolean isAttributeNameChar(char c) {
        return !Character.isWhitespace(c) && c != '=' && c != '>' && c != '/' && c != '"' && c != '\'';
    }

    private boolean lookingAt(String prefix) {
        return input.startsWith(prefix, pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(peek())) {
            pos++;
        }
    }

    private void skipUntilAfter(char c) {
        int end = input.indexOf(c, pos);
        pos = end < 0 ? input.length() : end + 1;
    }

    private char peek() {
        return input.charAt(pos);
    }
}
