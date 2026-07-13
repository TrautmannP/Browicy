package com.browicy.acid3;

import lombok.RequiredArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@RequiredArgsConstructor
public class Acid3Test {

    private static final Acid3Harness.RunResult RESULTS = Acid3Harness.run();

    @Parameterized.Parameters(name = "Acid3 test {0}")
    public static Collection<Object[]> tests() {
        Collection<Object[]> tests = new ArrayList<>(Acid3Harness.TEST_COUNT);
        for (int index = 0; index < Acid3Harness.TEST_COUNT; index++) {
            tests.add(new Object[]{index});
        }
        return tests;
    }

    private final int index;

    @Test
    public void acid3Subtest() {
        Acid3Harness.TestResult result = RESULTS.tests().get(index);
        assertEquals(result.message(), Acid3Harness.Status.PASS, result.status());
    }
}
