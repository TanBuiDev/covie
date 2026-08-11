package vn.covie.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;

@Component
public class JpaOutboxEventWriter implements OutboxEventWriter {
    private final OutboxEventRepository repository;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final ObjectMapper mapper;

    public JpaOutboxEventWriter(
            OutboxEventRepository repository, UuidV7Generator ids, AppClock clock, ObjectMapper mapper) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(OutboxEventIntent intent) {
        UUID id = ids.generate();
        repository.save(OutboxEventEntity.pending(
                id,
                intent.eventType(),
                intent.aggregateType(),
                intent.aggregateId(),
                mapper.valueToTree(intent.payload()),
                clock.now()));
        return id;
    }
}
