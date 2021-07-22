package io.fermibubble.fst;

/**
 * FairThrottle is a common interface for the self-tuning fair throttle implementations in this package
 *
 * Use one FairThrottle per remote endpoint (load balancer, fleet, etc.). Sharing a FairThrottle between multiple
 * endpoints will make it useless. Using one per thread or client locally will make it converge much more slowly
 * and lose its fairness properties.
 *
 * FairThrottle implementations should be thread safe.
 */
public interface FairThrottle {

    /**
     * Apply the throttle
     * @param key String identifying the client that you want to be fair to
     * @return ThrottleResult
     */
    ThrottleResult shouldAccept(String key);

    /**
     * ThrottleResult represents the result of a throttling decision ('true' means allow), and provides methods for a
     * client to call back to tell the throttle whether a result was successful or not.
     *
     * The client SHOULD call one of onSuccess() or onFailure() when the call it makes completes, if it fails to call,
     * the throttle accuracy will be lost. The client MUST NOT call either onSuccess() or onFailure() if isAllowed() is
     * false.
     */
    interface ThrottleResult {
        boolean isAllowed();
        void onSuccess();
        void onFailure();
    }
}
