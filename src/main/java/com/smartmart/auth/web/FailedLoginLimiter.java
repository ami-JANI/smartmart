package com.smartmart.auth.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket of failed logins for each username and IP address (FR-AUTH-8,
 * GUIDELINES.md section 7). A bucket holds {@value #CAPACITY} tokens and each
 * failed login takes one. The bucket fills up again one {@link #REFILL_PERIOD}
 * after its first failure, so after five failures within a minute, the next
 * attempt is refused until that minute has passed.
 * <p>
 * Kept in memory: a restart clears it, which is acceptable for one branch.
 */
class FailedLoginLimiter {

    static final int CAPACITY = 5;
    static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    FailedLoginLimiter(Clock clock) {
        this.clock = clock;
    }

    /** How long this username and address must wait, or empty if it may try now. */
    Optional<Duration> waitBeforeNextAttempt(String username, String address) {
        Instant now = clock.instant();
        Bucket bucket = buckets.get(key(username, address));
        if (bucket == null || bucket.refillsBy(now) || bucket.tokens() > 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, bucket.refillAt()));
    }

    void recordFailure(String username, String address) {
        Instant now = clock.instant();
        buckets.values().removeIf(bucket -> bucket.refillsBy(now));
        buckets.merge(key(username, address), new Bucket(CAPACITY - 1, now.plus(REFILL_PERIOD)),
                (old, fresh) -> new Bucket(Math.max(old.tokens() - 1, 0), old.refillAt()));
    }

    /** A successful login refills the bucket, so earlier typing mistakes do not count later. */
    void recordSuccess(String username, String address) {
        buckets.remove(key(username, address));
    }

    private static String key(String username, String address) {
        return username + "\n" + address;
    }

    private record Bucket(int tokens, Instant refillAt) {

        boolean refillsBy(Instant now) {
            return !now.isBefore(refillAt);
        }
    }
}
