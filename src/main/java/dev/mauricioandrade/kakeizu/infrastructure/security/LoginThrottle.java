package dev.mauricioandrade.kakeizu.infrastructure.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * After too many wrong passwords in a row, every login attempt is refused for a while.
 * There is only one user, so the counter is global.
 */
@ApplicationScoped
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(1);

    private final Clock clock;
    private int failures;
    private Instant lockedUntil = Instant.MIN;

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean isLocked() {
        return clock.instant().isBefore(lockedUntil);
    }

    public synchronized void recordFailure() {
        failures++;
        if (failures >= MAX_FAILURES) {
            lockedUntil = clock.instant().plus(LOCKOUT);
            failures = 0;
        }
    }

    public synchronized void recordSuccess() {
        failures = 0;
    }
}
