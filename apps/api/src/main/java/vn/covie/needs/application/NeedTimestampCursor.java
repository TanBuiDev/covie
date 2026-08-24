package vn.covie.needs.application;

import java.time.Instant;
import java.util.UUID;

public record NeedTimestampCursor(int v, Instant createdAt, UUID id) {
    public NeedTimestampCursor(Instant createdAt, UUID id) { this(1, createdAt, id); }
}
