package io.fermibubble.fst.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeProviderTest {
    @Test
    public void testTime() {
        final long t1 = TimeProvider.DEFAULT.nanoTime();
        final long t2 = TimeProvider.DEFAULT.nanoTime();
        assertTrue(t2 > t1);
    }
}
