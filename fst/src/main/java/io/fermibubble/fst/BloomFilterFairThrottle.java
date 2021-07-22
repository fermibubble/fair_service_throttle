package io.fermibubble.fst;

import io.fermibubble.fst.time.TimeProvider;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link FairThrottle} is a throttle designed to run in a service to protect a downstream service by sending only the
 * amount of traffic it can handle and no more. It also attempts to be 'fair' to a set of customers (identified by some
 * key string), preventing any single customer from consuming all the available capacity.
 *
 * Fairness uses a keyed hash-based scheme, loosely inspired by Stochastic Fair Queueing and Stochastic Fair BLUE.
 * Instead of one token bucket, think of a bloom filter of token buckets. Each customer is assigned to a set of
 * buckets based on their key string, and a slowly changing tweak value. A request is allowed only is all the token
 * buckets the customer hashes to allow it.
 *
 * This provides some fairness, because a very greedy 'key' will only have an impact on other keys that has to the
 * same set of buckets.
 *
 * Compared to {@link StochasticFairThrottle}, this one provides less fairness for small numbers of customers, but
 * should scale better (with less 'crosstalk') for large number of customers. For most use cases, pick
 * {@link StochasticFairThrottle}
 *
 * This implementation is thread safe.
 */
public class BloomFilterFairThrottle implements FairThrottle {
    private static final double UPDATE_TWEAK_NS = 60 * 1e9;

    private final ThrottleResult falseResult = new BloomFilterThrottleResult(false, null);
    private final TokenBucket[] tokenBuckets;
    private final int probes;
    private final TimeProvider timeProvider;
    // The tweak is a variable that periodically updated to ensure that is collisions happen, then only happen for
    // short period of time.
    private volatile int tweak;
    private final AtomicLong lastTweakUpdate;

    public BloomFilterFairThrottle(final double initialTps, final int buckets, final TimeProvider timeProvider) {
        this.probes = Math.min(3, buckets);
        this.tweak = ThreadLocalRandom.current().nextInt();
        this.timeProvider = checkNotNull(timeProvider);
        this.lastTweakUpdate = new AtomicLong(timeProvider.nanoTime());
        this.tokenBuckets = new TokenBucket[buckets];
        final SharedAIMDTokenBucket.SharedAIMD aimd = new SharedAIMDTokenBucket.SharedAIMD(initialTps);
        for(int i = 0; i < buckets; i++) {
            tokenBuckets[i] = new SharedAIMDTokenBucket(100, timeProvider, aimd);
        }
    }

    /**
     * A request is allowed through is ALL the token buckets for the request key allow the request. If the
     * request is allowed, a token is consumed from all buckets. If the request is denied, then no token is consumed.
     */
    @Override
    public ThrottleResult shouldAccept(String key) {
        updateTweak();
        final int[] hashKeys = HashUtils.generateNHashes(key, tweak, probes, tokenBuckets.length);
        for(int i = 0; i < probes; i++) {
            if(!tokenBuckets[hashKeys[i]].wouldAllow())
                return falseResult;
        }
        for(int i = 0; i < probes; i++)
            tokenBuckets[hashKeys[i]].claimToken();
        return new BloomFilterThrottleResult(true, hashKeys);
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
     * BloomFilterThrottleResult represents the result of a throttling decision ('true' means allow), and provides
     * methods for a client to call back to tell the throttle whether a result was successful or not.
     *
     * The client SHOULD call one of onSuccess() or onFailure() when the call it makes completes, if it fails to call,
     * the throttle accuracy will be lost. The client MUST NOT call either onSuccess() or onFailure() if isAllowed() is
     * false.
     */
    private final class BloomFilterThrottleResult implements ThrottleResult {
        private final boolean result;
        private final int[] keys;

        private BloomFilterThrottleResult(final boolean result, final int[] keys) {
            this.result = result;
            this.keys = keys;
        }

        @Override
        public boolean isAllowed() {
            return result;
        }

        @Override
        public void onSuccess() {
            checkArgument(result, "onSuccess() must only be called if the call was not throttled");
            for(int key : keys)
                tokenBuckets[key].onSuccess();
        }

        @Override
        public void onFailure() {
            checkArgument(result, "onFailure() must only be called if the call was not throttled");
            for(int key : keys)
                tokenBuckets[key].onFailure();
        }
    }
}
