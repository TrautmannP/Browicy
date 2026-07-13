package com.browicy.engine.css;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpecificityTest {

    @Test
    public void comparesIdsBeforeClassesAndClassesBeforeElements() {
        assertTrue(new Specificity(1, 0, 0).compareTo(new Specificity(0, 100, 100)) > 0);
        assertTrue(new Specificity(0, 1, 0).compareTo(new Specificity(0, 0, 100)) > 0);
        assertTrue(new Specificity(0, 0, 2).compareTo(new Specificity(0, 0, 1)) > 0);
    }

    @Test
    public void calculatesSimpleSelectorSpecificity() {
        assertEquals(new Specificity(0, 0, 1),
                new SimpleSelector("p", null, List.of()).specificity());
        assertEquals(new Specificity(0, 1, 0),
                new SimpleSelector(null, null, List.of("notice")).specificity());
        assertEquals(new Specificity(1, 1, 1),
                new SimpleSelector("p", "warning", List.of("notice")).specificity());
        assertEquals(Specificity.ZERO,
                new SimpleSelector("*", null, List.of()).specificity());
    }
}
