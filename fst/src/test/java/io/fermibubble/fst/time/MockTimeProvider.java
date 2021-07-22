package io.fermibubble.fst.time;

public class MockTimeProvider implements TimeProvider {
    public long t;

    @Override
    public long nanoTime() {
        return t;
    }
}
