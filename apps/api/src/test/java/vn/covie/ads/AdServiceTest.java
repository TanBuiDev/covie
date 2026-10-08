package vn.covie.ads;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.merchants.security.*;
import vn.covie.media.application.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.*;
import vn.covie.ads.api.*;
import vn.covie.ads.api.AdCampaignRequest.*;
class AdServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AdminAccessPolicy admins=mock(AdminAccessPolicy.class);
    final MerchantAccessPolicy merchants=mock(MerchantAccessPolicy.class);
    final CatalogItemRepository items=mock(CatalogItemRepository.class);
    final Instant now=Instant.parse("2026-10-08T00:00:00Z");
    final AdService service=new AdService(jdbc, admins, merchants, mock(MediaAttachmentPolicy.class), mock(MediaAssetRepository.class), mock(MediaUrlResolver.class), items, mock(AuditLogWriter.class), new UuidV7Generator(), ()->now);
    AdCampaignRequest request(String target) {return new AdCampaignRequest("Sale", "Body", "View",target,CreativeType.CSS,Template.FLASH,null,Placement.IN_FEED,Device.ALL,now,now.plusSeconds(3600),true,10);}
    @Test void sellerCannotAdvertiseAnotherMerchant() {
        UUID merchant=UUID.randomUUID();
        when(merchants.require(merchant,MerchantCapability.MANAGE_CATALOG_STRUCTURE)).thenReturn(new MerchantAccess(UUID.randomUUID(),merchant,UUID.randomUUID(),vn.covie.merchants.model.MerchantRole.OWNER));
        assertThrows(RuntimeException.class,()->service.create(merchant,request("/merchants/"+UUID.randomUUID())));
        verifyNoInteractions(jdbc);
    }
    @Test void adminStillCannotCreateUnsafeDestination() {
        when(admins.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(),Set.of(SystemRole.ADMIN),UUID.randomUUID()));
        assertThrows(RuntimeException.class,()->service.create(null,request("javascript:alert(1)")));
        verifyNoInteractions(jdbc);
    }
    @Test void statisticsRejectUnboundedDateRangesBeforeQuerying() {
        when(admins.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(),Set.of(SystemRole.ADMIN),UUID.randomUUID()));
        assertThrows(RuntimeException.class,()->service.stats(null,UUID.randomUUID(),LocalDate.of(2025,1,1),LocalDate.of(2026,1,1)));
        verifyNoInteractions(jdbc);
    }
    @Test void listingRejectsNegativeOrExcessiveOffsetsBeforeQuerying() {
        when(admins.requireAdmin()).thenReturn(new CurrentActor(UUID.randomUUID(),Set.of(SystemRole.ADMIN),UUID.randomUUID()));
        assertThrows(RuntimeException.class,()->service.list(null,-1));
        assertThrows(RuntimeException.class,()->service.list(null,1000001));
        verifyNoInteractions(jdbc);
    }
}
