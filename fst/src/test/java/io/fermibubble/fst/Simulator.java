package io.fermibubble.fst;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.fermibubble.fst.time.MockTimeProvider;
import io.fermibubble.fst.time.TimeProvider;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class Simulator {
    public static class TimeStep {
        long startTime;
        double value;
        TimeStep(final long startTime, final double value) { this.startTime = startTime; this.value = value; }
    }

    public static TimeStep ts(final double startTime, final double value) {
        return new TimeStep((long) startTime, value);
    }

    // Return a random number evenly distributed over [0, v)
    private static double jittered(final double value) {
        return 2 * ThreadLocalRandom.current().nextDouble() * value;
    }

    static long nextClientTime(final Iterable<SimulatedClient> clients) {
        long next = Long.MAX_VALUE;
        for(final SimulatedClient client : clients) {
            if(client.nextAttempt < next)
                next = client.nextAttempt;
        }
        return next;
    }

    private List<SimulatedClient> makeClients(final List<Double> requestTps, final TimeProvider t
            , final FairThrottle ft, final SimulatedServer server) {
        final List<SimulatedClient> result = new ArrayList<>();
        int i = 0;
        for(final double tps : requestTps) {
            result.add(new SimulatedClient(tps, t, ft, format("client_%d_%2.2f", i++, tps), server));
        }
        return result;
    }

    public void runSimulation(final SimulationConfig config) throws IOException {
        final MockTimeProvider mt = new MockTimeProvider();
        final double initialTps = requireNonNull(config.getServerGoodput().peek()).value;
        final SimulatedServer s= new SimulatedServer(config.getServerGoodput(), mt, config.getServerConstantFailureRate());
        final FairThrottle ft = SimulationConfig.FairThrottleType.STOCHASTIC_FAIR_THROTTLE.equals(config.getFairThrottleType())
                ? new StochasticFairThrottle(new StochasticFairThrottle.Config().withTimeProvider(mt).withInitialTps(initialTps))
                : new BloomFilterFairThrottle(initialTps, config.getBuckets(), mt);
        final List<SimulatedClient> clients = makeClients(config.getClientRequestTps(), mt, ft, s);
        final PrintWriter pw = new PrintWriter(new FileWriter(config.getOutputFile()));
        double lastMetrics = 0;
        pw.println("t,goodput,throttled,offered,type,name");
        while (mt.nanoTime() < config.getRunUntil()) {
            mt.t = nextClientTime(clients);
            for(final SimulatedClient client : clients) client.call();
            if((mt.t - lastMetrics) > config.getTimeStepSec() * 1e9) {
                lastMetrics = mt.t;
                s.printMetrics(pw);
                for(final SimulatedClient client : clients) client.printMetrics(pw);
            }
        }
        pw.close();
    }

    public static class SimulatedServer {
        private final TimeProvider time;
        private final SharedAIMDTokenBucket bucket;
        private final SharedAIMDTokenBucket.SharedAIMD aimd;
        private final Queue<TimeStep> goodputTps;
        private int successes;
        private int offered;
        private int throttled;
        private final Random random = new Random();
        private double constantFailureRate = 0.0d;

        SimulatedServer(final Queue<TimeStep> goodputTps, final TimeProvider t, final double constantFailureRate) {
            this.time = t;
            this.goodputTps = goodputTps;
            final double initialTps = requireNonNull(goodputTps.poll()).value;
            this.aimd = new SharedAIMDTokenBucket.SharedAIMD(initialTps);
            this.bucket = new SharedAIMDTokenBucket((int) initialTps, t, aimd);
            this.constantFailureRate = constantFailureRate;
        }

        SimulatedServer(final Queue<TimeStep> goodputTps, final TimeProvider t) {
            this(goodputTps, t, 0.0d);
        }

        SimulatedServer(final double goodputTps, final TimeProvider t) {
            this(Lists.newLinkedList(ImmutableList.of(ts(0, goodputTps))), t);
        }

        boolean call() {
            offered++;
            if((!goodputTps.isEmpty()) && (goodputTps.peek().startTime < time.nanoTime())) {
                aimd.setTargetTps(requireNonNull(goodputTps.poll()).value);
            }
            if(bucket.wouldAllow() && random.nextDouble() > constantFailureRate) {
                bucket.claimToken();
                successes++;
                return true;
            }
            throttled++;
            return false;
        }

        void printMetrics(final PrintWriter pw) {
            pw.printf("%f, %d, %d, %d, %s, %s\n", time.nanoTime()/1e9, successes, throttled, offered, "server", "server");
            successes = 0;
            throttled = 0;
            offered = 0;
        }
    }

    public static class TestResult {
        int offered;
        int throttled;
        int successes;

        @Override
        public String toString() {
            return "TestResult{" +
                    "offered=" + offered +
                    ", throttled=" + throttled +
                    ", successes=" + successes +
                    "}";
        }

        public String toStringDetailed() {
            return "TestResult{" +
                    "offered=" + offered +
                    ", throttled=" + throttled +
                    ", successes=" + successes +
                    ", SuccessRateOfOffers=" + getSuccessRateOfOffers() +
                    ", ThrottleRateOfAttempts=" + getThrottledRateOfAttempts() +
                    "}";
        }

        public double getSuccessRateOfOffers() {
            return 1.0d * successes / offered;
        }

        public double getThrottledRateOfAttempts() {
            return 1.0d * throttled / (throttled + offered);
        }

        public TestResult cloneAndReset() {
            final TestResult clone = new TestResult();
            clone.offered = this.offered;
            clone.throttled = this.throttled;
            clone.successes = this.successes;
            this.offered = 0;
            this.throttled = 0;
            this.successes = 0;
            return clone;
        }
    }

    public static class SimulatedClient {
        final TestResult result;

        private final TimeProvider time;
        private final FairThrottle ft;
        private final Supplier<String> keyProvider;
        private final SimulatedServer server;
        private final Queue<TimeStep> tpsUpdateStream;

        private long attemptEvery;
        private long nextAttempt;

        SimulatedClient(final double initialTps, final TimeProvider t, final FairThrottle ft
                , final String key, final SimulatedServer server) {
            this(ImmutableList.of(ts(0, initialTps)), t, ft, () -> key, server);
        }

        SimulatedClient(final List<TimeStep> tpsUpdateStream, final TimeProvider t, final FairThrottle ft
                , final Supplier<String> keyProvider, final SimulatedServer server) {
            this.tpsUpdateStream = Lists.newLinkedList(tpsUpdateStream);
            final double initialTps = tpsUpdateStream.get(0).value;
            this.attemptEvery = (long) (1e9/initialTps);
            this.nextAttempt = t.nanoTime() + (long) jittered(attemptEvery);
            this.result = new TestResult();
            this.time = t;
            this.ft =  ft;
            this.keyProvider = keyProvider;
            this.server = server;
        }

        void call() {
            if(time.nanoTime() >= nextAttempt) {
                checkForTpsUpdate();
                nextAttempt = time.nanoTime() + attemptEvery;
                final String key = keyProvider.get();
                final FairThrottle.ThrottleResult tr = ft.shouldAccept(key);
                if(tr.isAllowed()) {
                    result.offered += 1;
                    if(server.call()) {
                        result.successes += 1;
                        tr.onSuccess();
                    } else {
                        tr.onFailure();
                    }
                } else {
                    result.throttled += 1;
                }
            }
        }

        private void checkForTpsUpdate() {
            if(!tpsUpdateStream.isEmpty() && tpsUpdateStream.peek().startTime < time.nanoTime()) {
                final TimeStep newTps = tpsUpdateStream.poll();
                attemptEvery = (long) (1e9 / requireNonNull(newTps).value);
            }
        }

        void printMetrics(final PrintWriter pw) {
            pw.printf("%f, %d, %d, %d, %s, %s\n", time.nanoTime()/1e9, result.successes, result.throttled
                    , result.offered, "client", keyProvider.get());
            result.successes = 0;
            result.throttled = 0;
            result.offered = 0;
        }
    }

}
