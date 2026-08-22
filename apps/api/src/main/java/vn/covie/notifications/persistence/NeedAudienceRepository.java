package vn.covie.notifications.persistence;

import java.util.List;
import java.util.UUID;

public interface NeedAudienceRepository {
    List<NeedAudienceLocation> findCandidateLocations(UUID needId, int limit);

    record NeedAudienceLocation(UUID merchantProfileId, UUID merchantLocationId, long distanceMeters) {}
}
