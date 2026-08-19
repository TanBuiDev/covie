package vn.covie.merchants.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.merchants.api.dto.CreateMerchantRequest;
import vn.covie.merchants.api.dto.MerchantSummaryResponse;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.RequestIdAccessor;

class MerchantSlugServiceTest {

    @Test
    void creationRetriesWithARandomUuidSuffixWhenThePriorSuffixIsOccupied() {
        UUID firstMerchantId = UUID.fromString("abcdef12-0000-7000-8000-000000000001");
        UUID secondMerchantId = UUID.fromString("abcdef12-0000-7000-8000-000000000002");
        MerchantProfileRepository profiles = mock(MerchantProfileRepository.class);
        when(profiles.existsBySlugAndDeletedAtIsNull("tap-hoa-minh")).thenReturn(true);
        when(profiles.existsBySlugAndDeletedAtIsNull("tap-hoa-minh-000001")).thenReturn(true);
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UuidV7Generator ids = mock(UuidV7Generator.class);
        when(ids.generate()).thenReturn(firstMerchantId, secondMerchantId, UUID.randomUUID());
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(UUID.randomUUID(), Set.of(SystemRole.USER), UUID.randomUUID()));
        MerchantApplicationService service = new MerchantApplicationService(actors, profiles, memberships,
                new MerchantSlugService(profiles), mock(MerchantAccessPolicy.class), mock(MediaAttachmentPolicy.class),
                ids, fixedClock(), mock(AuditLogWriter.class), mock(RequestIdAccessor.class));

        MerchantSummaryResponse response = service.create(new CreateMerchantRequest("Tạp hóa Minh", null));

        assertThat(response.slug()).isEqualTo("tap-hoa-minh-000002");
        verify(ids, times(3)).generate();
    }

    private static AppClock fixedClock() {
        return () -> Instant.parse("2026-01-01T00:00:00Z");
    }
}
