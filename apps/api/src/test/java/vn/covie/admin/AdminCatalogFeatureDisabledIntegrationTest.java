package vn.covie.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.shared.security.SystemRole;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
class AdminCatalogFeatureDisabledIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SystemRoleAssignmentRepository roles;
    @Autowired InitialSessionIssuer issuer;

    @Test void newAdminReadsAreDisabledByDefault() throws Exception {
        UUID adminId=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(adminId,"Disabled feature admin",Instant.now()));
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),adminId,SystemRole.ADMIN,Instant.now()));
        String bearer="Bearer " + issuer.issue(adminId,new DeviceMetadata("Test","Disabled feature")).accessToken().value();
        mvc.perform(get("/api/v1/admin/catalog-items").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/catalog-items/" + UUID.randomUUID()).header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/catalog-items/" + UUID.randomUUID() + "/moderation-cases")
                .header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/catalog-items/" + UUID.randomUUID() + "/moderation-cases")
                .header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType("application/json").content("{\"reasonCode\":\"SPAM\",\"note\":\"Review\"}"))
                .andExpect(status().isNotFound());
    }
}
