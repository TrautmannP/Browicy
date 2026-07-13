package com.browicy.engine.selectors;

import java.util.ArrayList;
import java.util.List;

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
                } else if (consume('+')) {
                    combinator = Combinator.ADJACENT_SIBLING;
                    skipWhitespace();
                } else if (consume('~')) {
                    combinator = Combinator.GENERAL_SIBLING;
                    skipWhitespace();
                } else if (whitespace) {
                    combinator = Combinator.DESCENDANT;
                } else {
                    throw error();
                }

                if (atEnd() || peek() == ',' || isCombinator(peek())) {
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
            List<AttributeSelector> attributes = new ArrayList<>();
            List<StructuralPseudoClass> pseudoClasses = new ArrayList<>();

            if (!atEnd() && consume('*')) {
                typeName = "*";
            } else if (!atEnd() && isTypeStart(peek())) {
                typeName = readTypeName();
            }

            while (!atEnd()) {
                if (peek() == '.' || peek() == '#') {
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
                } else if (peek() == '[') {
                    attributes.add(parseAttributeSelector());
                } else if (peek() == ':') {
                    pseudoClasses.add(parsePseudoClass());
                } else {
                    break;
                }
            }

            if (typeName == null && id == null && classes.isEmpty()
                    && attributes.isEmpty() && pseudoClasses.isEmpty()) {
                throw error();
            }
            return new CompoundSelector(typeName, id, classes, attributes, pseudoClasses);
        }

        private AttributeSelector parseAttributeSelector() {
            consume('[');
            skipWhitespace();
            String name = readIdentifier();
            skipWhitespace();
            if (consume(']')) {
                return new AttributeSelector(name, AttributeSelector.Operator.PRESENT, null);
            }

            AttributeSelector.Operator operator;
            if (consume('=')) {
                operator = AttributeSelector.Operator.EQUALS;
            } else if (consume('~') && consume('=')) {
                operator = AttributeSelector.Operator.INCLUDES;
            } else {
                throw error();
            }
            skipWhitespace();
            String value = readQuotedString();
            skipWhitespace();
            if (!consume(']')) {
                throw error();
            }
            return new AttributeSelector(name, operator, value);
        }

        private String readQuotedString() {
            if (atEnd() || (peek() != '\'' && peek() != '"')) {
                throw error();
            }
            char quote = source.charAt(position++);
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char value = source.charAt(position++);
                if (value == quote) {
                    return result.toString();
                }
                if (value == '\\') {
                    if (atEnd()) {
                        throw error();
                    }
                    value = source.charAt(position++);
                }
                result.append(value);
            }
            throw error();
        }

        private StructuralPseudoClass parsePseudoClass() {
            consume(':');
            String name = readIdentifier().toLowerCase(java.util.Locale.ROOT);
            if ("first-child".equals(name)) {
                return StructuralPseudoClass.firstChild();
            }
            if ("last-child".equals(name)) {
                return StructuralPseudoClass.lastChild();
            }
            if (!"nth-child".equals(name) || !consume('(')) {
                throw error();
            }
            int start = position;
            while (!atEnd() && peek() != ')') {
                position++;
            }
            if (atEnd()) {
                throw error();
            }
            String formula = source.substring(start, position);
            position++;
            int[] coefficients = parseNthFormula(formula);
            return StructuralPseudoClass.nthChild(coefficients[0], coefficients[1]);
        }

        private int[] parseNthFormula(String sourceFormula) {
            String formula = sourceFormula.replaceAll("\\s+", "")
                    .toLowerCase(java.util.Locale.ROOT);
            if ("odd".equals(formula)) {
                return new int[]{2, 1};
            }
            if ("even".equals(formula)) {
                return new int[]{2, 0};
            }
            try {
                int n = formula.indexOf('n');
                if (n < 0) {
                    return new int[]{0, Integer.parseInt(formula)};
                }
                if (n != formula.lastIndexOf('n')) {
                    throw error();
                }
                String aSource = formula.substring(0, n);
                int a = aSource.isEmpty() || "+".equals(aSource) ? 1
                        : "-".equals(aSource) ? -1 : Integer.parseInt(aSource);
                String bSource = formula.substring(n + 1);
                int b = bSource.isEmpty() ? 0 : Integer.parseInt(bSource);
                return new int[]{a, b};
            } catch (NumberFormatException exception) {
                throw error();
            }
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

        private static boolean isCombinator(char value) {
            return value == '>' || value == '+' || value == '~';
        }
    }
}
