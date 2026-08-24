package vn.covie.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.moderation.persistence.ModerationRiskHistoryRepository;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;

class JdbcHybridModerationRiskServiceTest {

    @Test
    void usesGlobalLookbackAndMerchantHistoryForCatalog() {
        SystemSettingResolver settings = Mockito.mock(SystemSettingResolver.class);
        ModerationRiskHistoryRepository history = Mockito.mock(ModerationRiskHistoryRepository.class);
        UUID merchantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        ModerationSubject subject = new ModerationSubject(ModerationTargetType.CATALOG_ITEM, null, merchantId, UUID.randomUUID(), null);
        when(settings.integer("moderation.hybrid_adverse_lookback_days", SystemSettingContext.globalOnly())).thenReturn(30);
        when(history.hasMerchantAdverseAction(merchantId, now.minus(30, ChronoUnit.DAYS))).thenReturn(true);

        boolean required = new JdbcHybridModerationRiskService(settings, history).requiresPreApproval(subject, now);

        assertThat(required).isTrue();
        verify(history).hasMerchantAdverseAction(merchantId, now.minus(30, ChronoUnit.DAYS));
    }

    @Test
    void usesBuyerHistoryForNeedWhenNoMerchantExists() {
        SystemSettingResolver settings = Mockito.mock(SystemSettingResolver.class);
        ModerationRiskHistoryRepository history = Mockito.mock(ModerationRiskHistoryRepository.class);
        UUID buyerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        ModerationSubject subject = new ModerationSubject(ModerationTargetType.NEED_REQUEST, buyerId, null, UUID.randomUUID(), UUID.randomUUID());
        when(settings.integer("moderation.hybrid_adverse_lookback_days", SystemSettingContext.globalOnly())).thenReturn(90);
        when(history.hasUserAdverseAction(buyerId, now.minus(90, ChronoUnit.DAYS))).thenReturn(false);

        assertThat(new JdbcHybridModerationRiskService(settings, history).requiresPreApproval(subject, now)).isFalse();
        verify(history).hasUserAdverseAction(buyerId, now.minus(90, ChronoUnit.DAYS));
    }
}
