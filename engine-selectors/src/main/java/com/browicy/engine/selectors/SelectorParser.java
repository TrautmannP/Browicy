package com.browicy.engine.selectors;

import java.util.ArrayList;
import java.util.List;

/** Parser für die von Browicy unterstützte CSS-Selektorteilmenge. */
public final class SelectorParser {

    public SelectorList parse(String source) {
        if (source == null || source.isBlank()) {
            throw new SelectorParseException(source, 0);
        }

        Parser parser = new Parser(source);
        List<ComplexSelector> selectors = new ArrayList<>();
        parser.skipWhitespace();
        while (!parser.atEnd()) {
            selectors.add(parser.parseComplexSelector());
            parser.skipWhitespace();
            if (parser.atEnd()) {
                break;
            }
            if (!parser.consume(',')) {
                throw parser.error();
            }
            parser.skipWhitespace();
            if (parser.atEnd()) {
                throw parser.error();
            }
        }
        return new SelectorList(selectors);
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private ComplexSelector parseComplexSelector() {
            List<SelectorStep> steps = new ArrayList<>();
            steps.add(new SelectorStep(parseCompoundSelector(), null));

            while (true) {
                boolean whitespace = skipWhitespace();
                if (atEnd() || peek() == ',') {
                    break;
                }

                Combinator combinator;
                if (consume('>')) {
                    combinator = Combinator.CHILD;
                    skipWhitespace();
                } else if (whitespace) {
                    combinator = Combinator.DESCENDANT;
                } else {
                    throw error();
                }

                if (atEnd() || peek() == ',' || peek() == '>') {
                    throw error();
                }
                steps.add(new SelectorStep(parseCompoundSelector(), combinator));
            }
            return new ComplexSelector(steps);
        }

        private CompoundSelector parseCompoundSelector() {
            String typeName = null;
            String id = null;
            List<String> classes = new ArrayList<>();

            if (!atEnd() && consume('*')) {
                typeName = "*";
            } else if (!atEnd() && isTypeStart(peek())) {
                typeName = readTypeName();
            }

            while (!atEnd() && (peek() == '.' || peek() == '#')) {
                char prefix = source.charAt(position++);
                String name = readIdentifier();
                if (prefix == '#') {
                    if (id != null) {
                        throw error();
                    }
                    id = name;
                } else {
                    classes.add(name);
                }
            }

            if (typeName == null && id == null && classes.isEmpty()) {
                throw error();
            }
            return new CompoundSelector(typeName, id, classes);
        }

        private String readTypeName() {
            int start = position++;
            while (!atEnd()) {
                char value = peek();
                if (!Character.isLetterOrDigit(value)
                        && value != '-' && value != '_') {
                    break;
                }
                position++;
            }
            return source.substring(start, position);
        }

        private String readIdentifier() {
            if (atEnd() || !isIdentifierStart(peek())) {
                throw error();
            }
            int start = position++;
            while (!atEnd() && isIdentifierPart(peek())) {
                position++;
            }
            String result = source.substring(start, position);
            if ("-".equals(result)) {
                throw error();
            }
            return result;
        }

        private boolean skipWhitespace() {
            int start = position;
            while (!atEnd() && Character.isWhitespace(peek())) {
                position++;
            }
            return position > start;
        }

        private boolean consume(char expected) {
            if (!atEnd() && peek() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private char peek() {
            return source.charAt(position);
        }

        private boolean atEnd() {
            return position >= source.length();
        }

        private SelectorParseException error() {
            return new SelectorParseException(source, position);
        }

        private static boolean isTypeStart(char value) {
            return Character.isLetter(value) || value == '_';
        }

        private static boolean isIdentifierStart(char value) {
            return Character.isLetter(value) || value == '_' || value == '-';
        }

        private static boolean isIdentifierPart(char value) {
            return isIdentifierStart(value) || Character.isDigit(value);
        }
    }
}
