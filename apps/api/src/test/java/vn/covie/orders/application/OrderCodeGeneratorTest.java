package vn.covie.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.covie.shared.time.AppClock;

class OrderCodeGeneratorTest {
    @Test
    void createsDisplayCodeWithUtcDateAndRandomSuffix() {
        AppClock clock = () -> Instant.parse("2026-08-22T04:00:00Z");
        OrderCodeGenerator generator = new OrderCodeGenerator(clock, new SecureRandom());

        assertThat(generator.generate()).matches("CV-260822-[A-Z0-9]{6}");
    }
}
