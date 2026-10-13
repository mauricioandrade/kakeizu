package dev.mauricioandrade.kakeizu.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    final MutableClock clock = new MutableClock();
    final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void locksAfterFiveFailuresInARowForOneMinute() {
        failTimes(4);
        assertFalse(throttle.isLocked());

        throttle.recordFailure();
        assertTrue(throttle.isLocked());

        clock.advance(Duration.ofSeconds(59));
        assertTrue(throttle.isLocked());
        clock.advance(Duration.ofSeconds(1));
        assertFalse(throttle.isLocked());
    }

    @Test
    void successResetsTheCount() {
        failTimes(4);
        throttle.recordSuccess();
        failTimes(4);

        assertFalse(throttle.isLocked());
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure();
        }
    }

    static class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-13T12:00:00Z");

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
