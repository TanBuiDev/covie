package vn.covie.shared.time;

import java.time.Clock;
import java.time.Instant;

public final class SystemAppClock implements AppClock {
    private final Clock clock;

    public SystemAppClock() {
        this(Clock.systemUTC());
    }

    SystemAppClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
