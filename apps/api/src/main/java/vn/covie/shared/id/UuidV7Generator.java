package vn.covie.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7Generator {

    private static final long UNIX_TS_MASK = 0x0000FFFFFFFFFFFFL;
    private final SecureRandom random;

    public UuidV7Generator() {
        this(new SecureRandom());
    }

    UuidV7Generator(SecureRandom random) {
        this.random = random;
    }

    public UUID generate() {
        long unixMillis = System.currentTimeMillis() & UNIX_TS_MASK;
        long randA = random.nextInt(1 << 12);
        long msb = (unixMillis << 16) | 0x7000L | randA;

        long lsb = random.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
