package io.fermibubble.fst;

/**
 * A TokenBucket is a simple rate throttle based on a bucket of given capacity, some rate of adding tokens into the
 * bucket, and attempts to take tokens out of the bucket.
 *
 * This TokenBucket has two special features:
 *  - Throttling is broken into two parts; wouldAllow() which says whether getting a token is allowed, and
 *    claimToken() which actually takes the token. Breaking it apart in this way does add race condition which can
 *    over commit tokens, but that debt is paid on the next cycle. The contract is to call wouldAllow(), and if it is
 *    'true' then optionally call claimToken() sometime soon after.
 *  - A control loop based on onSuccess() and onFailure() which can change the token addition rate to try meet maximum
 *    system goodput
 */
public interface TokenBucket {
    boolean wouldAllow();
    void claimToken();
    void onSuccess();
    void onFailure();
}
