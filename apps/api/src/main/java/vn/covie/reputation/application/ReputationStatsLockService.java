package vn.covie.reputation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.covie.reputation.model.MerchantReputationStatsEntity;
import vn.covie.reputation.model.UserReputationStatsEntity;
import vn.covie.reputation.persistence.MerchantReputationStatsRepository;
import vn.covie.reputation.persistence.UserReputationStatsRepository;

@Service
public class ReputationStatsLockService {
    private final UserReputationStatsRepository users; private final MerchantReputationStatsRepository merchants;
    public ReputationStatsLockService(UserReputationStatsRepository users, MerchantReputationStatsRepository merchants) { this.users=users; this.merchants=merchants; }
    public UserReputationStatsEntity user(UUID userId, Instant now) { users.ensureEmpty(userId, now); return users.findForUpdate(userId).orElseThrow(); }
    public MerchantReputationStatsEntity merchant(UUID merchantId, Instant now) { merchants.ensureEmpty(merchantId, now); return merchants.findForUpdate(merchantId).orElseThrow(); }
}
