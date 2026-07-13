package com.browicy.engine.selectors;

/** Fehler beim Parsen eines ungültigen oder nicht unterstützten Selektors. */
public final class SelectorParseException extends IllegalArgumentException {

    private final String selector;
    private final int position;

    SelectorParseException(String selector, int position) {
        super(message(selector, position));
        this.selector = selector;
        this.position = position;
    }

    public String getSelector() {
        return selector;
    }

    public int getPosition() {
        return position;
    }

    private static String message(String selector, int position) {
        return "Ungültiger oder nicht unterstützter Selektor an Position "
                + position + ": " + selector;
    }
}
