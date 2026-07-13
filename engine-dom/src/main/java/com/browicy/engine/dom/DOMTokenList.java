package com.browicy.engine.dom;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Veränderbare, mit dem {@code class}-Attribut synchronisierte Token-Liste.
 */
public final class DOMTokenList {

    private final Element element;

    DOMTokenList(Element element) {
        this.element = Objects.requireNonNull(element, "element");
    }

    public int getLength() {
        return tokens().size();
    }

    public String item(int index) {
        List<String> values = tokens();
        return index >= 0 && index < values.size() ? values.get(index) : null;
    }

    public boolean contains(String token) {
        validateToken(token);
        return tokens().contains(token);
    }

    public void add(String... tokens) {
        List<String> validated = validateTokens(tokens);
        LinkedHashSet<String> current = tokenSet();
        if (current.addAll(validated)) {
            write(current);
        }
    }

    public void remove(String... tokens) {
        List<String> validated = validateTokens(tokens);
        LinkedHashSet<String> current = tokenSet();
        if (current.removeAll(validated)) {
            write(current);
        }
    }

    public boolean toggle(String token) {
        validateToken(token);
        LinkedHashSet<String> current = tokenSet();
        if (current.remove(token)) {
            write(current);
            return false;
        }
        current.add(token);
        write(current);
        return true;
    }

    public boolean toggle(String token, boolean force) {
        validateToken(token);
        LinkedHashSet<String> current = tokenSet();
        boolean present = current.contains(token);
        if (force && !present) {
            current.add(token);
            write(current);
        } else if (!force && present) {
            current.remove(token);
            write(current);
        }
        return force;
    }

    public String getValue() {
        String value = element.getAttribute("class");
        return value == null ? "" : value;
    }

    public void setValue(String value) {
        element.setAttribute("class", value == null ? "" : value);
    }

    List<String> tokens() {
        String value = element.getAttribute("class");
        if (value == null || value.isEmpty()) {
            return List.of();
        }

        Set<String> result = new LinkedHashSet<>();
        int start = -1;
        for (int index = 0; index <= value.length(); index++) {
            boolean separator = index == value.length() || isAsciiWhitespace(value.charAt(index));
            if (!separator && start < 0) {
                start = index;
            } else if (separator && start >= 0) {
                result.add(value.substring(start, index));
                start = -1;
            }
        }
        return List.copyOf(result);
    }

    private LinkedHashSet<String> tokenSet() {
        return new LinkedHashSet<>(tokens());
    }

    private void write(Set<String> values) {
        element.setAttribute("class", String.join(" ", values));
    }

    private static List<String> validateTokens(String[] tokens) {
        Objects.requireNonNull(tokens, "tokens");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            validateToken(token);
            result.add(token);
        }
        return result;
    }

    private static void validateToken(String token) {
        if (token == null || token.isEmpty()) {
            throw DomException.syntax("Ein DOMToken darf nicht leer sein");
        }
        for (int index = 0; index < token.length(); index++) {
            if (isAsciiWhitespace(token.charAt(index))) {
                throw DomException.invalidCharacter(
                        "Ein DOMToken darf keine ASCII-Leerzeichen enthalten: " + token);
            }
        }
    }

    private static boolean isAsciiWhitespace(char value) {
        return value == '\t' || value == '\n' || value == '\f' || value == '\r' || value == ' ';
    }

    @Override
    public String toString() {
        return getValue();
    }
}
