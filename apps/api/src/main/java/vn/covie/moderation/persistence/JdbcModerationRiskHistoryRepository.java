package vn.covie.moderation.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcModerationRiskHistoryRepository implements ModerationRiskHistoryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcModerationRiskHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasMerchantAdverseAction(UUID merchantId, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists (
                select 1
                from moderation_actions ma
                join moderation_cases mc on mc.id = ma.moderation_case_id
                left join catalog_items ci on ci.id = mc.target_catalog_item_id
                where ma.created_at >= :since
                  and ma.action_type in ('REJECT','HIDE','SUSPEND')
                  and (mc.target_merchant_profile_id = :merchantId or ci.merchant_profile_id = :merchantId)
            )
            """, Map.of("merchantId", merchantId, "since", since), Boolean.class));
    }

    @Override
    public boolean hasUserAdverseAction(UUID userId, Instant since) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists (
                select 1
                from moderation_actions ma
                join moderation_cases mc on mc.id = ma.moderation_case_id
                left join need_requests n on n.id = mc.target_need_request_id
                where ma.created_at >= :since
                  and ma.action_type in ('REJECT','HIDE','SUSPEND')
                  and (mc.target_user_id = :userId or n.buyer_user_id = :userId)
            )
            """, Map.of("userId", userId, "since", since), Boolean.class));
    }
}
