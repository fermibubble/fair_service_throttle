package io.fermibubble.fst;

import io.fermibubble.fst.time.TimeProvider;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * StochasticFairThrottle is a {@link FairThrottle} which attempts to allocate teh available call rate to a
 * downstream resource fairly across multiple customers, while minimizing traffic that the downstream service
 * needs to reject and maximizing the use of the available goodput. All in O(1) space and time.
 *
 * It does this by allocating customers to one of an array of token buckets, which they potentially share with (many)
 * other customers (a bit like a hash table)
 *
 * The has is keyed with a time varying key (tweak), which is rotated periodically. This has the effect of shuffling
 * customers around and changing which other customers they share with. This scheme is inspired by -
 * "Stochastic Fair Queueing, a fairly standard networking technique (see "Stochastic Fairness Queuing" by McKenny)"
 *
 * Goodput optimization is handled by an AIMD control loop in the buckets themselves. See {@link SharedAIMDTokenBucket}
 * for details on how that works.
 *
 * Use one FairThrottle per remote endpoint (load balancer, fleet, etc.). Sharing a FairThrottle between multiple
 * endpoints will make it useless. Using one per thread or client locally will make it converge much more slowly
 * and lose its fairness properties.
 *
 * StochasticFairThrottle is thread-safe.
 */
public class StochasticFairThrottle implements FairThrottle {
    private static final double UPDATE_TWEAK_NS = 5 * 1e9;

    private final ThrottleResult falseResult = new StochasticThrottleResult(false, -1);
    private final SharedAIMDTokenBucket[] tokenBuckets;
    private final TimeProvider timeProvider;

    private volatile int tweak;
    private final AtomicLong lastTweakUpdate;

    public StochasticFairThrottle() {
        this(new Config());
    }

    public StochasticFairThrottle(final Config config) {
        this.tweak = ThreadLocalRandom.current().nextInt();
        this.timeProvider = config.getTimeProvider();
        this.lastTweakUpdate = new AtomicLong(timeProvider.nanoTime());
        this.tokenBuckets = makeTokenBuckets(config);
    }

    private SharedAIMDTokenBucket[] makeTokenBuckets(final Config config) {
        final SharedAIMDTokenBucket.SharedAIMD aimd = new SharedAIMDTokenBucket.SharedAIMD(config.getInitialTps()
                , config.getCeilingTps(), config.getFloorTps());
        final SharedAIMDTokenBucket[] tokenBuckets = new SharedAIMDTokenBucket[config.getBuckets()];
        for(int i = 0; i < config.getBuckets(); i++)
            tokenBuckets[i] = new SharedAIMDTokenBucket((int) config.getInitialTps(), config.getTimeProvider(), aimd);
        return tokenBuckets;
    }


    @Override
    public ThrottleResult shouldAccept(String key) {
        updateTweak();
        final int hashKey = HashUtils.tweakedHash(key, tweak, tokenBuckets.length);
        if(tokenBuckets[hashKey].wouldAllow()) {
            tokenBuckets[hashKey].claimToken();
            return new StochasticThrottleResult(true, hashKey);
        }
        return falseResult;
    }

    private void updateTweak() {
        final long lastUpdate = lastTweakUpdate.get();
        final long now = timeProvider.nanoTime();
        if((now - lastUpdate) > UPDATE_TWEAK_NS) {
            if(lastTweakUpdate.compareAndSet(lastUpdate, now))
                tweak = ThreadLocalRandom.current().nextInt();
        }
    }

    /**
     * StochasticThrottleResult represents the result of a throttling decision ('true' means allow), and provides
     * methods for a client to call back to tell the throttle whether a result was successful or not.
     *
     * The client SHOULD call one of onSuccess() or onFailure() when the call it makes completes, if it fails to call,
     * the throttle accuracy will be lost. The client MUST NOT call either onSuccess() or onFailure() if isAllowed() is
     * false.
     */
    private final class StochasticThrottleResult implements ThrottleResult {
        private final boolean result;
        private final int key;

        private StochasticThrottleResult(final boolean result, final int key) {
            this.result = result;
            this.key = key;
        }

        @Override
        public boolean isAllowed() {
            return result;
        }

        @Override
        public void onSuccess() {
            checkArgument(result, "onSuccess() must only be called if the call was not throttled");
            tokenBuckets[key].onSuccess();
        }

        @Override
        public void onFailure() {
            checkArgument(result, "onFailure() must only be called if the call was not throttled");
            tokenBuckets[key].onFailure();
        }
    }

    /**
     * Config is responsible for holding configuration needed for {@link StochasticFairThrottle} instance.
     *
     * The default constructor creates a ready to use Config object. Just override the fields you need using
     * corresponding 'with' method.
     */
    public static final class Config {
        private static final double DEFAULT_INITIAL_TPS = 100.0d;
        private static final int DEFAULT_BUCKETS = 17;

        private TimeProvider timeProvider = TimeProvider.DEFAULT;
        private int buckets = DEFAULT_BUCKETS;
        private double initialTps = DEFAULT_INITIAL_TPS;
        private double floorTps = SharedAIMDTokenBucket.SharedAIMD.DEFAULT_FLOOR_TPS;
        private double ceilingTps = SharedAIMDTokenBucket.SharedAIMD.DEFAULT_CEILING_TPS;

        public Config withTimeProvider(final TimeProvider timeProvider) {
            this.timeProvider = checkNotNull(timeProvider);
            return this;
        }

        public Config withBuckets(final int buckets) {
            checkArgument(buckets > 0);
            this.buckets = buckets;
            return this;
        }

        public Config withInitialTps(final double initialTps) {
            checkArgument(initialTps > 0.0d);
            this.initialTps = initialTps;
            return this;
        }

        public Config withTpsRange(final double floorTps, final double ceilingTps) {
            checkArgument(floorTps > 0.0d);
            checkArgument(ceilingTps > 0.0d);
            checkArgument(floorTps <= ceilingTps);
            this.floorTps = floorTps;
            this.ceilingTps = ceilingTps;
            return this;
        }

        public TimeProvider getTimeProvider() {
            return timeProvider;
        }

        public int getBuckets() {
            return buckets;
        }

        public double getInitialTps() {
            return initialTps;
        }

        public double getFloorTps() {
            return floorTps;
        }

        public double getCeilingTps() {
            return ceilingTps;
        }
    }
}
