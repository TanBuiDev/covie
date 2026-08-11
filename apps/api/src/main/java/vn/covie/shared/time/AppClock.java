package vn.covie.shared.time;

import java.time.Instant;

@FunctionalInterface
public interface AppClock {
    Instant now();
}
