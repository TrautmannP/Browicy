package com.browicy.engine.css;

/**
 * Vereinfachte CSS-Spezifität aus ID-, Klassen- und Elementanteil.
 * Der Vergleich erfolgt lexikographisch in genau dieser Reihenfolge.
 */
public record Specificity(int ids, int classes, int elements)
        implements Comparable<Specificity> {

    public static final Specificity ZERO = new Specificity(0, 0, 0);

    public Specificity {
        if (ids < 0 || classes < 0 || elements < 0) {
            throw new IllegalArgumentException("Spezifitätswerte dürfen nicht negativ sein");
        }
    }

    @Override
    public int compareTo(Specificity other) {
        int comparison = Integer.compare(ids, other.ids);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(classes, other.classes);
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(elements, other.elements);
    }
}
