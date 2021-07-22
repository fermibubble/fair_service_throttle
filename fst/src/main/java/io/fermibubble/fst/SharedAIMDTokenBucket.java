package io.fermibubble.fst;

import com.google.common.util.concurrent.AtomicDouble;
import io.fermibubble.fst.time.TimeProvider;

import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * SharedAIMDTokenBucket implements an AIMD TokenBucket based on shared AIMD parameters. This allows multiple buckets
 * to have independent throttles, while sharing a control loop that lets them dial in the right system-wide throughput.
 *
 * This Atomic-based implementation is bit more complex than the lock-based implementation would be, but is about
 * 5x faster under thread contention (especially in the case that the system is running against the throttle).
 *
 */
public class SharedAIMDTokenBucket implements TokenBucket {
    private final SharedAIMD aimd;
    private final double capacity;
    private final AtomicDouble tokens;
    private final TimeProvider timeProvider;
    private final AtomicLong lastRefillNs;

    public SharedAIMDTokenBucket(final int capacity, final TimeProvider timeProvider, final SharedAIMD aimd) {
        this.timeProvider = timeProvider;
        this.capacity = capacity;
        this.aimd = checkNotNull(aimd);
        this.tokens = new AtomicDouble(capacity);
        this.lastRefillNs = new AtomicLong(timeProvider.nanoTime());
    }

    /**
     * Non-blocking refill of the token bucket, using atomics.
     * This is a little awkward because we lost too much efficiency if we try do the long swap of both the 'tokens'
     * and 'lastRefillNs' at the same time. It' worth the awkwardness, though, because it's about 5x cheaper
     * than the lock-based version.
     *
     * Note (refer code below):
     * Cap the 'tokensToAdd' to try not to overshoot. Because we are using a stale version of 'lastTokens'
     * and this whole operation isn't atomic, there is a possibility of overshooting or undershooting slightly at
     * the top of the bucket. This should only happen under contention.
     *
     * The race that exceeds capacity works like this:
     *  - Time 2e9, T1 executes now = 2e9, tokensToAdd = 1, lastRefillNs = 2e9, lastTokens = 0, tokensToAdd = 1
     *  - Time 3e9, T2 executes now = 3ep, tokensToAdd = 1, lastRefillNs = 3e9, lastTokens = 0, tokensToAdd = 1
     *  - Tine 4e9, T1 adds tokens, tokens = 1.0
     *  - Time 5e9, T2 adds tokens, tokens = 2.0 (exceeds capacity)
     *
     * To prevent this cap tokensToAdd = Math.min(tokensToAdd, capacity - lastTokens);
     */
    private double refill() {
        while(true) {
            final long now = timeProvider.nanoTime();
            final long lastRefill = lastRefillNs.get();
            double tokensToAdd = aimd.getTargetTps() * ((now - lastRefill) / 1e9);
            if(tokensToAdd < 1) return tokens.get();
            if(lastRefillNs.compareAndSet(lastRefill, now)) {
                final double lastTokens = tokens.get();
                tokensToAdd = Math.min(tokensToAdd, capacity - lastTokens);
                return tokens.addAndGet(tokensToAdd);
            }
        }
    }

    @Override
    public boolean wouldAllow() {
        if(tokens.get() > 1.0d) return true;
        else return refill() > 1.0d;
    }

    @Override
    public void claimToken() {
        tokens.addAndGet(-1.0d);
    }

    @Override
    public void onSuccess() {
        aimd.onSuccess();
    }

    @Override
    public void onFailure() {
        aimd.onFailure();
    }

    /**
     * SharedAIMD is a set of AIMD parameters that are shared across multiple SharedAIMDTokenBuckets
     */
    public static class SharedAIMD {
        static final double DEFAULT_ADDITIVE_FACTOR = 1.0d;
        static final double DEFAULT_MULTIPLICATIVE_FACTOR = 0.7d;
        static final double DEFAULT_FLOOR_TPS = 5d;
        static final double DEFAULT_CEILING_TPS = Double.MAX_VALUE;

        private final AtomicDouble targetTps;
        private final double ceilingTps;
        private final double floorTps;

        public SharedAIMD(final double initialTps) {
            this(initialTps, DEFAULT_CEILING_TPS, DEFAULT_FLOOR_TPS);
        }

        public SharedAIMD(final double initialTps, final double ceilingTps, final double floorTps) {
            checkArgument(0 <= floorTps);
            checkArgument(floorTps <= ceilingTps);
            checkArgument(floorTps <= initialTps);
            checkArgument(initialTps <= ceilingTps);
            this.targetTps = new AtomicDouble(initialTps);
            this.ceilingTps = ceilingTps;
            this.floorTps = floorTps;
        }

        double getTargetTps() {
            return targetTps.doubleValue();
        }

        void onSuccess() {
            this.targetTps.set(Math.min(ceilingTps, targetTps.doubleValue() + DEFAULT_ADDITIVE_FACTOR));
        }
        void onFailure() {
            this.targetTps.set(Math.max(floorTps, targetTps.doubleValue() * DEFAULT_MULTIPLICATIVE_FACTOR));
        }

        public void setTargetTps(final double targetTps) {
            this.targetTps.set(targetTps);
        }

    }
}
