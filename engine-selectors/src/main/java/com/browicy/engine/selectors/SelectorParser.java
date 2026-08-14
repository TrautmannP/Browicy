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
                if (atEnd() || peek() == ',' || peek() == ')') {
                    break;
                }
                if (steps.getLast().selector().pseudoElement() != null) {
                    throw error();
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
            String typeNamespace = null;
            String typeName = null;
            String id = null;
            List<String> classes = new ArrayList<>();
            List<AttributeSelector> attributes = new ArrayList<>();
            List<StructuralPseudoClass> pseudoClasses = new ArrayList<>();
            List<String> statePseudoClasses = new ArrayList<>();
            List<PseudoClassFunction> functions = new ArrayList<>();
            String pseudoElement = null;

            if (!atEnd() && consume('|')) {
                typeNamespace = "";
                typeName = readTypeOrUniversal();
            } else if (!atEnd() && consume('*')) {
                typeName = "*";
                if (consume('|')) {
                    typeNamespace = "*";
                    typeName = readTypeOrUniversal();
                }
            } else if (!atEnd() && isTypeStart(peek())) {
                typeName = readTypeName();
                if (consume('|')) {
                    typeNamespace = typeName;
                    typeName = readTypeOrUniversal();
                }
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
                    if (position + 1 < source.length() && source.charAt(position + 1) == ':') {
                        if (pseudoElement != null) throw error();
                        position += 2;
                        pseudoElement = readIdentifier().toLowerCase(java.util.Locale.ROOT);
                        if (!isPseudoElementName(pseudoElement)) {
                            throw error();
                        }
                        if (!atEnd() && peek() != ',' && !Character.isWhitespace(peek())
                                && !isCombinator(peek())) throw error();
                    } else {
                        int start = position;
                        position++;
                        String name = readIdentifier().toLowerCase(java.util.Locale.ROOT);
                        if (isPseudoElementName(name)) {
                            if (pseudoElement != null) throw error();
                            pseudoElement = name;
                            if (!atEnd() && peek() != ',' && !Character.isWhitespace(peek())
                                    && !isCombinator(peek())) throw error();
                        } else {
                            position = start;
                            parsePseudoClass(pseudoClasses, statePseudoClasses, functions);
                        }
                    }
                } else {
                    break;
                }
            }

            if (typeName == null && id == null && classes.isEmpty()
                    && attributes.isEmpty() && pseudoClasses.isEmpty()
                    && statePseudoClasses.isEmpty() && functions.isEmpty()
                    && pseudoElement == null) {
                throw error();
            }
            return new CompoundSelector(typeNamespace, typeName, id, classes, attributes,
                    pseudoClasses, statePseudoClasses, functions, pseudoElement);
        }

        private AttributeSelector parseAttributeSelector() {
            consume('[');
            skipWhitespace();
            String namespace = null;
            String name;
            if (consume('*')) {
                if (!consume('|')) {
                    throw error();
                }
                namespace = "*";
                name = readIdentifier();
            } else if (consume('|')) {
                namespace = "";
                name = readIdentifier();
            } else {
                String first = readIdentifier();
                if (consume('|')) {
                    namespace = first;
                    name = readIdentifier();
                } else {
                    name = first;
                }
            }
            skipWhitespace();
            if (consume(']')) {
                return new AttributeSelector(namespace, name, AttributeSelector.Operator.PRESENT,
                        null);
            }

            AttributeSelector.Operator operator;
            if (consume('=')) {
                operator = AttributeSelector.Operator.EQUALS;
            } else if (consume('~') && consume('=')) {
                operator = AttributeSelector.Operator.INCLUDES;
            } else if (consume('*') && consume('=')) {
                operator = AttributeSelector.Operator.CONTAINS;
            } else if (consume('^') && consume('=')) {
                operator = AttributeSelector.Operator.PREFIX_MATCH;
            } else if (consume('$') && consume('=')) {
                operator = AttributeSelector.Operator.SUFFIX_MATCH;
            } else {
                throw error();
            }
            skipWhitespace();
            String value = peek() == '\'' || peek() == '"'
                    ? readQuotedString() : readIdentifier();
            skipWhitespace();
            if (!consume(']')) {
                throw error();
            }
            return new AttributeSelector(namespace, name, operator, value);
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

        private void parsePseudoClass(List<StructuralPseudoClass> pseudoClasses,
                                      List<String> statePseudoClasses,
                                      List<PseudoClassFunction> functions) {
            consume(':');
            String name = readIdentifier().toLowerCase(java.util.Locale.ROOT);
            if ("hover".equals(name) || "checked".equals(name)
                    || "focus".equals(name) || "active".equals(name)
                    || "disabled".equals(name) || "enabled".equals(name)
                    || "link".equals(name) || "visited".equals(name)
                    || "target".equals(name) || "indeterminate".equals(name)
                    || "focus-visible".equals(name) || "focus-within".equals(name)
                    || "placeholder-shown".equals(name)) {
                statePseudoClasses.add(name);
                return;
            }
            if ("first-child".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.firstChild());
                return;
            }
            if ("root".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.root());
                return;
            }
            if ("only-child".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.onlyChild());
                return;
            }
            if ("only-of-type".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.onlyOfType());
                return;
            }
            if ("empty".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.empty());
                return;
            }
            if ("first-of-type".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.firstOfType());
                return;
            }
            if ("last-child".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.lastChild());
                return;
            }
            if ("last-of-type".equals(name)) {
                pseudoClasses.add(StructuralPseudoClass.lastOfType());
                return;
            }
            PseudoClassFunction.Kind kind = switch (name) {
                case "is" -> PseudoClassFunction.Kind.IS;
                case "where" -> PseudoClassFunction.Kind.WHERE;
                case "not" -> PseudoClassFunction.Kind.NOT;
                default -> null;
            };
            if (kind != null) {
                if (!consume('(')) {
                    throw error();
                }
                skipWhitespace();
                List<ComplexSelector> arguments = new ArrayList<>();
                arguments.add(parseComplexSelector());
                skipWhitespace();
                while (consume(',')) {
                    skipWhitespace();
                    arguments.add(parseComplexSelector());
                    skipWhitespace();
                }
                if (!consume(')')) {
                    throw error();
                }
                for (ComplexSelector argument : arguments) {
                    if (argument.pseudoElement() != null) {
                        throw error();
                    }
                }
                functions.add(new PseudoClassFunction(kind, new SelectorList(arguments)));
                return;
            }
            boolean nthChild = "nth-child".equals(name);
            boolean nthOfType = "nth-of-type".equals(name);
            boolean nthLastChild = "nth-last-child".equals(name);
            boolean nthLastOfType = "nth-last-of-type".equals(name);
            if ((!nthChild && !nthOfType && !nthLastChild && !nthLastOfType)
                    || !consume('(')) {
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
            if (nthChild) {
                pseudoClasses.add(StructuralPseudoClass.nthChild(coefficients[0], coefficients[1]));
            } else if (nthOfType) {
                pseudoClasses.add(StructuralPseudoClass.nthOfType(coefficients[0], coefficients[1]));
            } else if (nthLastChild) {
                pseudoClasses.add(StructuralPseudoClass.nthLastChild(
                        coefficients[0], coefficients[1]));
            } else {
                pseudoClasses.add(StructuralPseudoClass.nthLastOfType(
                        coefficients[0], coefficients[1]));
            }
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

        private String readTypeOrUniversal() {
            if (consume('*')) {
                return "*";
            }
            if (!atEnd() && isTypeStart(peek())) {
                return readTypeName();
            }
            throw error();
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

        private boolean skipWhitespace() {            int start = position;
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

        private static boolean isPseudoElementName(String name) {
            return name.equals("before") || name.equals("after")
                    || name.equals("first-letter") || name.equals("first-line");
        }

        private static boolean isCombinator(char value) {
            return value == '>' || value == '+' || value == '~';
        }
    }
}
