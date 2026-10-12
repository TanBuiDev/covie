package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.needs.application.NeedDiscoveryCursor;
import vn.covie.needs.application.NeedDiscoveryCursorCodec;
import vn.covie.support.PostgresIntegrationTest;

@SpringBootTest(properties = {"covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
@Transactional
class NeedDiscoveryPaginationIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcNeedDiscoveryRepository repository;
    @Autowired ObjectMapper mapper;

    @Test
    void serializedCursorTraversesNonExactRealRankTiesWithoutRepeatsOrOmissions() throws Exception {
        try (var stream = getClass().getResourceAsStream("/needs/discovery-rank-ties.sql")) {
            assertThat(stream).isNotNull();
            jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        UUID merchant = jdbc.queryForObject("select md5('pilot-merchant-1')::uuid", UUID.class);
        UUID location = jdbc.queryForObject("select md5('pilot-location-1')::uuid", UUID.class);
        var expected = repository.findCandidates(merchant, Set.of(location), location, Instant.now(), null, 100);
        assertThat(expected).hasSize(20);
        assertThat(expected).extracting(NeedMatchRow::textRank).containsOnly(expected.getFirst().textRank());
        double rank = expected.getFirst().textRank();
        assertThat(rank).isNotEqualTo((double) (float) rank);
        var codec = new NeedDiscoveryCursorCodec(mapper);
        var observed = new ArrayList<UUID>();
        NeedDiscoveryCursor cursor = null;
        for (int page = 0; page < 5; page++) {
            var rows = repository.findCandidates(merchant, Set.of(location), location, Instant.now(), cursor, 5);
            if (page == 4) {
                assertThat(rows).isEmpty();
                break;
            }
            assertThat(rows).hasSize(5);
            if (!observed.isEmpty()) {
                assertThat(rows).extracting(NeedMatchRow::needId).doesNotContainAnyElementsOf(observed);
            }
            rows.forEach(row -> observed.add(row.needId()));
            var last = rows.getLast();
            cursor = codec.decode(codec.encode(new NeedDiscoveryCursor(last.textRank(), last.distanceMeters(),
                    last.needId(), last.merchantLocationId())));
        }
        assertThat(observed).containsExactlyElementsOf(expected.stream().map(NeedMatchRow::needId).toList());
    }
}
