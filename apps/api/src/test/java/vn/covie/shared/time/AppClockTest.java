package vn.covie.shared.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppClockTest {

    @Test
    void fixedClockCanDriveDeterministicBusinessTime() {
        Instant expected = Instant.parse("2026-08-08T08:00:00Z");
        AppClock clock = () -> expected;

        assertThat(clock.now()).isEqualTo(expected);
    }
}
