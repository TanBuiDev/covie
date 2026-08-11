package vn.covie.shared.outbox;

import java.util.UUID;

public interface OutboxEventWriter {
    UUID append(OutboxEventIntent intent);
}
