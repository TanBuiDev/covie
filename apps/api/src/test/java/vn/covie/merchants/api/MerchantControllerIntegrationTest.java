package vn.covie.merchants.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.application.MerchantSlugService;
import vn.covie.merchants.application.MerchantApplicationService;
import vn.covie.merchants.api.dto.CreateMerchantRequest;
import vn.covie.merchants.api.dto.MerchantSummaryResponse;
import vn.covie.merchants.api.dto.UpdateMerchantRequest;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.media.application.MediaAttachmentPolicy;


class MerchantControllerIntegrationTest {

    @Test
    void normalizesVietnameseMerchantNamesIntoStableSlugs() {
        MerchantSlugService slugs = new MerchantSlugService(mock(MerchantProfileRepository.class));

        assertThat(slugs.uniqueSlug("Tạp hóa Minh", UUID.randomUUID())).isEqualTo("tap-hoa-minh");
    }

    @Test
    void createsMerchantAndOwnerMembershipInOneApplicationTransaction() {
        UUID actorId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(actorId, Set.of(SystemRole.USER), UUID.randomUUID()));
        MerchantProfileRepository profiles = mock(MerchantProfileRepository.class);
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        AuditLogWriter audit = mock(AuditLogWriter.class);
        MerchantApplicationService service = service(actors, profiles, memberships, mock(MerchantAccessPolicy.class), audit);

        MerchantSummaryResponse created = service.create(new CreateMerchantRequest("  Tạp hóa Minh  ", "  Local shop  "));

        assertThat(created.name()).isEqualTo("Tạp hóa Minh");
        assertThat(created.slug()).isEqualTo("tap-hoa-minh");
        assertThat(created.currentUserRole()).isEqualTo(MerchantRole.OWNER);
        InOrder writes = inOrder(profiles, memberships, audit);
        writes.verify(profiles).save(any(MerchantProfileEntity.class));
        writes.verify(memberships).save(any(MerchantMembershipEntity.class));
        writes.verify(audit).securityEvent(eq(actorId), eq("MERCHANT_CREATED"), eq("MERCHANT"),
                eq(created.id()), any(), eq(Map.of()));
    }

    @Test
    void updateRequiresProvidedVersionAndUsesMediaPolicyForNewAttachments() {
        UUID merchantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID logoId = UUID.randomUUID();
        MerchantProfileEntity merchant = MerchantProfileEntity.create(merchantId, "Before", "before", Instant.EPOCH);
        MerchantProfileRepository profiles = mock(MerchantProfileRepository.class);
        when(profiles.findByIdAndDeletedAtIsNull(merchantId)).thenReturn(java.util.Optional.of(merchant));
        when(profiles.saveAndFlush(merchant)).thenReturn(merchant);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(actorId, Set.of(SystemRole.USER), UUID.randomUUID()));
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        when(access.require(merchantId, MerchantCapability.EDIT_PROFILE))
                .thenReturn(new MerchantAccess(actorId, merchantId, UUID.randomUUID(), MerchantRole.OWNER));
        MediaAttachmentPolicy media = mock(MediaAttachmentPolicy.class);
        MerchantApplicationService service = service(actors, profiles, mock(MerchantMembershipRepository.class), access,
                mock(AuditLogWriter.class), media);

        service.update(merchantId, "\"v0\"", new UpdateMerchantRequest("After", null, logoId, null));

        verify(media).requireMerchantLogo(logoId, actorId);
        assertThat(merchant.getName()).isEqualTo("After");
    }

    private MerchantApplicationService service(CurrentActorProvider actors, MerchantProfileRepository profiles,
            MerchantMembershipRepository memberships, MerchantAccessPolicy access, AuditLogWriter audit) {
        return service(actors, profiles, memberships, access, audit, new MediaAttachmentPolicy() {
            @Override public void requireMerchantLogo(UUID mediaId, UUID ownerUserId) {}
            @Override public void requireMerchantCover(UUID mediaId, UUID ownerUserId) {}
        });
    }

    private MerchantApplicationService service(CurrentActorProvider actors, MerchantProfileRepository profiles,
            MerchantMembershipRepository memberships, MerchantAccessPolicy access, AuditLogWriter audit,
            MediaAttachmentPolicy media) {
        return new MerchantApplicationService(actors, profiles, memberships, new MerchantSlugService(profiles), access,
                media, new UuidV7Generator(), () -> Instant.parse("2026-01-01T00:00:00Z"), audit,
                mock(RequestIdAccessor.class));
    }
}
