package vn.covie.needs.persistence;

import java.util.List;
import java.util.UUID;
import vn.covie.needs.model.NeedType;

public interface NeedEligibleCandidateRepository {
    List<NeedEligibleCandidateRow> findCandidates(UUID merchantId, UUID locationId,
            NeedType needType, NeedEligibleCandidateKey after, int limit);
}
