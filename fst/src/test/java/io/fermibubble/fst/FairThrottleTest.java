package io.fermibubble.fst;

import com.google.common.collect.ImmutableList;
import io.fermibubble.fst.time.MockTimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FairThrottleTest {
    private MockTimeProvider time;

    void runTest(final MockTimeProvider time, final Iterable<Simulator.SimulatedClient> clients, final double runFor) {
        final long baseT = time.t;
        while(time.t < ((1e9 * runFor) + baseT)) {
            time.t = Simulator.nextClientTime(clients);
            for(final Simulator.SimulatedClient client : clients) {
                client.call();
            }
        }
    }

    @BeforeEach
    void beforeEach() {
        this.time = new MockTimeProvider();
    }

    @Test
    void testBloomFilterFairThrottle_SimpleConstantCase() {
        final FairThrottle ft = new BloomFilterFairThrottle(100, 10, time);
        final Simulator.SimulatedServer server = new Simulator.SimulatedServer(10, time);
        final Simulator.SimulatedClient c1 = new Simulator.SimulatedClient(1000, time, ft, "c1", server);
        runTest(time, ImmutableList.of(c1), 100);
        assertTrue(c1.result.offered < 2000); // Less than 2x overshoot
        assertTrue(c1.result.successes > 900); // More than 90% throughput;
    }

    @Test
    void testStochasticFairThrottle_SimpleConstantCase() {
        final FairThrottle ft = new StochasticFairThrottle(new StochasticFairThrottle.Config()
                .withInitialTps(100)
                .withBuckets(10)
                .withTimeProvider(time));
        final Simulator.SimulatedServer server = new Simulator.SimulatedServer(10, time);
        final Simulator.SimulatedClient c1 = new Simulator.SimulatedClient(1000, time, ft, "c1", server);
        runTest(time, ImmutableList.of(c1), 100);
        assertTrue(c1.result.offered < 4000); // Less than 4x overshoot
        assertTrue(c1.result.successes > 900); // More than 90% throughput;
    }

    @Test
    void testStochasticFairThrottle_CustomTpsRange_HitsFloor() {
        final FairThrottle ft = new StochasticFairThrottle(new StochasticFairThrottle.Config()
                .withInitialTps(100)
                .withTpsRange(0.1, Double.MAX_VALUE)
                .withBuckets(10)
                .withTimeProvider(time));
        final Simulator.SimulatedServer server = new Simulator.SimulatedServer(10, time);
        final Simulator.SimulatedClient c1 = new Simulator.SimulatedClient(1000, time, ft, "c1", server);
        runTest(time, ImmutableList.of(c1), 100);
        assertTrue(c1.result.offered < 2000); // Less than 2x overshoot
        assertTrue(c1.result.successes > 300); // More than 30% throughput;
    }

    @Test
    void testStochasticFairThrottle_CustomTpsRange_HitsCeiling() {
        final FairThrottle ft = new StochasticFairThrottle(new StochasticFairThrottle.Config()
                .withInitialTps(100)
                .withTpsRange(0.1, 1000)
                .withBuckets(10)
                .withTimeProvider(time));
        final Simulator.SimulatedServer server = new Simulator.SimulatedServer(10_000, time);

        // Run c1 for 10 seconds such that AIMD hits ceiling
        final Simulator.SimulatedClient c1 = new Simulator.SimulatedClient(500, time, ft, "key", server);
        runTest(time, ImmutableList.of(c1), 10);
        assertEquals(c1.result.offered, c1.result.successes);
        assertTrue(c1.result.successes > 4900); // More than 98% throughput;
        assertTrue(c1.result.throttled < 100); // AIMD increases sufficiently fast to not throttle

        // Run second client c2 at higher rate, ensure every attempt succeeds
        final Simulator.SimulatedClient c2 = new Simulator.SimulatedClient(1000, time, ft, "key", server);
        runTest(time, ImmutableList.of(c2), 10);
        assertEquals(c2.result.offered, c2.result.successes);
        assertTrue(c2.result.successes > 9990); // More than 99.9% throughput;
        assertEquals(0, c2.result.throttled);
    }

}
