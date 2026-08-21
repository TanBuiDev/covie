package vn.covie.orders.application;

import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.shared.time.AppClock;

@Component
public final class OrderCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd")
            .withZone(ZoneOffset.UTC);

    private final AppClock clock;
    private final SecureRandom random;

    @Autowired
    public OrderCodeGenerator(AppClock clock) {
        this(clock, new SecureRandom());
    }

    OrderCodeGenerator(AppClock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public String generate() {
        StringBuilder code = new StringBuilder("CV-").append(DATE_FORMAT.format(clock.now())).append('-');
        for (int index = 0; index < 6; index++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
