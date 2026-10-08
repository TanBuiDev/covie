package vn.covie.needs.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import vn.covie.needs.application.NeedDiscoveryCursor;

public interface NeedDiscoveryRepository {
    List<NeedMatchRow> findCandidates(UUID merchantId, Set<UUID> allowedLocationIds, UUID requestedLocationId,
            Instant now, NeedDiscoveryCursor after, int candidateLimit);

    List<NeedMatchRow> findCandidateForNeed(UUID merchantId, UUID locationId, UUID needId, Instant now);
}
