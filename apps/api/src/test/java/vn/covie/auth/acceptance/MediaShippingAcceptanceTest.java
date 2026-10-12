package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.*;
import vn.covie.media.storage.*;

@AutoConfigureMockMvc
class MediaShippingAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired vn.covie.media.persistence.MediaAssetRepository media;
    @MockitoBean ObjectStorage storage;
    private static final String FIXED="{\"mode\":\"FIXED\",\"baseFee\":20000,\"currencyCode\":\"VND\",\"status\":\"ACTIVE\",\"rules\":[]}";

    @Test void shippingOwnershipVersionsAndDuplicateActivePolicyAreEnforcedThroughHttp() throws Exception {
        var owner=activeUser("Shipping owner"); var outsider=activeUser("Shipping outsider");
        String bearer=token(owner.getId()),other=token(outsider.getId()); String path=shippingFixture(owner.getId());
        var staff=activeUser("Unassigned shipping staff");
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),UUID.fromString(path.split("/")[4]),
                staff.getId(),MerchantRole.STAFF,Instant.now()));
        mvc.perform(get(path).header("Authorization",token(staff.getId()))).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization",other).contentType(MediaType.APPLICATION_JSON).content(FIXED))
                .andExpect(status().isNotFound());
        var created=mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(FIXED))
                .andExpect(status().isCreated()).andExpect(header().string("ETag","\"v0\"")).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(FIXED))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SHIPPING_POLICY_DUPLICATE_ACTIVE"));
        mvc.perform(patch(path+"/"+id).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(FIXED))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(patch(path+"/"+id).header("Authorization",bearer).header("If-Match","\"v9\"")
                .contentType(MediaType.APPLICATION_JSON).content(FIXED)).andExpect(status().isConflict());
        mvc.perform(patch(path+"/"+id).header("Authorization",bearer).header("If-Match","\"v0\"")
                .contentType(MediaType.APPLICATION_JSON).content(FIXED.replace("20000","25000")))
                .andExpect(status().isOk()).andExpect(header().string("ETag","\"v1\""));
        mvc.perform(get(path).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].baseFee").value(25000));
        assertThat(jdbc.queryForObject("select count(*) from shipping_policies where id=?",Long.class,UUID.fromString(id))).isEqualTo(1);
    }

    @Test void concurrentShippingCreatesKeepOneActivePolicy() throws Exception {
        var owner=activeUser("Racing shipping owner"); String bearer=token(owner.getId()),path=shippingFixture(owner.getId());
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> create=()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post(path).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(FIXED))
                        .andReturn().getResponse().getStatus();
            };
            var first=workers.submit(create); var second=workers.submit(create);
            assertThat(java.util.List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201,409);
        }
        UUID location=UUID.fromString(path.split("/")[6]);
        assertThat(jdbc.queryForObject("select count(*) from shipping_policies where merchant_location_id=? and policy_status='ACTIVE'",Long.class,location)).isEqualTo(1);
    }

    @Test void mediaCompletionHidesForeignOwnershipAndReplaysOneVerifiedUpload() throws Exception {
        var owner=activeUser("Media owner"); var outsider=activeUser("Media outsider");
        String bearer=token(owner.getId()),other=token(outsider.getId());
        when(storage.presignPut(any(),any(),anyLong(),any())).thenReturn(new PresignedUpload(java.net.URI.create("https://upload.test/signed"),"PUT",Instant.now().plusSeconds(600),java.util.Map.of()));
        var created=mvc.perform(post("/api/v1/media/uploads").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"USER_AVATAR\",\"contentType\":\"image/png\",\"fileSize\":8}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.storageKey").doesNotExist()).andReturn();
        UUID id=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getResponse().getContentAsString()).path("mediaId").asText());
        String path="/api/v1/media/"+id+"/complete",key=media.findById(id).orElseThrow().getStorageKey();
        mvc.perform(post(path).header("Authorization",other).header("Idempotency-Key",UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        verify(storage,never()).head(key);
        when(storage.head(key)).thenReturn(new StoredObjectMetadata("image/png",8));
        when(storage.readPrefix(key,32)).thenReturn(new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a});
        String completionKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(path).header("Authorization",bearer).header("Idempotency-Key",completionKey))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mediaId").value(id.toString()));
        assertThat(media.findById(id).orElseThrow().getMediaStatus()).isEqualTo(vn.covie.media.model.MediaStatus.READY);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='MEDIA_UPLOAD_COMPLETED' and entity_id=?",Long.class,id)).isEqualTo(1);
        verify(storage,times(1)).head(key); verify(storage,never()).deleteIfExists(key);
    }

    private String token(UUID user) { return "Bearer "+issuer.issue(user,device()).accessToken().value(); }
    private String shippingFixture(UUID owner) {
        UUID merchant=UUID.randomUUID(),location=UUID.randomUUID(); Instant now=Instant.now();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Shipping shop","shipping-"+merchant,now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner,MerchantRole.OWNER,now));
        locations.saveAndFlush(MerchantLocationEntity.create(location,merchant,"Main","1 CoVie",10.8,106.7,now));
        return "/api/v1/merchants/"+merchant+"/locations/"+location+"/shipping-policies";
    }
}
