package com.smartmart.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** The token bucket behind FR-AUTH-8, with a clock the test moves by hand. */
class FailedLoginLimiterTest {

    private static final String ADDRESS = "10.0.0.7";

    private final MovableClock clock = new MovableClock();
    private final FailedLoginLimiter limiter = new FailedLoginLimiter(clock);

    private void fail(String username, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(username, ADDRESS);
            clock.advance(Duration.ofSeconds(1));
        }
    }

    @Test
    void fourFailuresStillAllowTheFifthAttempt() {
        fail("rahim", 4);

        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).isEmpty();
    }

    @Test
    void fiveFailuresBlockTheSixthAttemptUntilTheMinuteHasPassed() {
        fail("rahim", 5);

        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).contains(Duration.ofSeconds(55));

        clock.advance(Duration.ofSeconds(54));
        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).contains(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).isEmpty();
    }

    @Test
    void failuresSpreadOverMoreThanAMinuteDoNotBlock() {
        fail("rahim", 4);
        clock.advance(Duration.ofMinutes(1));
        fail("rahim", 1);

        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).isEmpty();
    }

    @Test
    void anotherUsernameOrAddressIsNotBlocked() {
        fail("rahim", 5);

        assertThat(limiter.waitBeforeNextAttempt("karim", ADDRESS)).isEmpty();
        assertThat(limiter.waitBeforeNextAttempt("rahim", "10.0.0.8")).isEmpty();
    }

    @Test
    void successfulLoginForgetsEarlierFailures() {
        fail("rahim", 4);
        limiter.recordSuccess("rahim", ADDRESS);
        fail("rahim", 4);

        assertThat(limiter.waitBeforeNextAttempt("rahim", ADDRESS)).isEmpty();
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-11-08T09:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
