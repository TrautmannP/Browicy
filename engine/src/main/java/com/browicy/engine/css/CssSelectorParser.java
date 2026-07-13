package com.browicy.engine.css;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CssSelectorParser {

    static Optional<SimpleSelector> parse(String source) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }

        String selector = source.strip();
        String tagName = null;
        String id = null;
        List<String> classes = new ArrayList<>();
        int position = 0;

        if (selector.charAt(position) == '*') {
            tagName = "*";
            position++;
        } else if (Character.isLetter(selector.charAt(position))) {
            int end = readTagName(selector, position);
            tagName = selector.substring(position, end);
            position = end;
        }

        while (position < selector.length()) {
            char prefix = selector.charAt(position++);
            if (prefix != '.' && prefix != '#') {
                return Optional.empty();
            }
            if (position >= selector.length() || !isIdentifierStart(selector.charAt(position))) {
                return Optional.empty();
            }
            int end = readIdentifier(selector, position);
            String name = selector.substring(position, end);
            if ("-".equals(name)) {
                return Optional.empty();
            }
            if (prefix == '#') {
                if (id != null) {
                    return Optional.empty();
                }
                id = name;
            } else {
                classes.add(name);
            }
            position = end;
        }

        if (tagName == null && id == null && classes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SimpleSelector(tagName, id, classes));
    }

    private static int readTagName(String source, int start) {
        int position = start + 1;
        while (position < source.length()) {
            char value = source.charAt(position);
            if (!Character.isLetterOrDigit(value) && value != '-') {
                break;
            }
            position++;
        }
        return position;
    }

    private static int readIdentifier(String source, int start) {
        int position = start + 1;
        while (position < source.length() && isIdentifierPart(source.charAt(position))) {
            position++;
        }
        return position;
    }

    private static boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '-';
    }

    private static boolean isIdentifierPart(char value) {
        return isIdentifierStart(value) || Character.isDigit(value);
    }
}
