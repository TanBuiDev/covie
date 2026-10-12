package vn.covie.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class AdminUserSafetyIntegrationTest {
    // A separate database keeps the global active-admin invariant independent of other suites' fixtures.
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("admin_safety_test").withUsername("covie").withPassword("covie_test");
    static { POSTGRES.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired AdminUserService service;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SystemRoleAssignmentRepository roles;
    @MockitoBean CurrentActorProvider actors;
    final ThreadLocal<CurrentActor> current = new ThreadLocal<>();

    @Test void competingSuspensionsKeepOneActiveAdminAndOnlyCommitWinningSideEffects() throws Exception {
        Instant now=Instant.now(); UUID first=admin(now), second=admin(now);
        when(actors.currentActor()).thenAnswer(invocation -> current.get());
        CyclicBarrier reads=new CyclicBarrier(2);
        doAnswer(invocation -> {
            long count=jdbc.queryForObject("select count(*) from system_role_assignments a join users u on u.id=a.user_id where a.role='ADMIN' and u.account_status='ACTIVE'",Long.class);
            // If commands are serialized, the first reader may proceed after this bounded wait.
            // Without serialization, both real database reads observe two admins before either writes.
            try { reads.await(500,TimeUnit.MILLISECONDS); }
            catch (TimeoutException | BrokenBarrierException ignored) { }
            return count;
        }).when(roles).countActiveUsersWithRole(SystemRole.ADMIN);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<ErrorCode> a=workers.submit(()->suspend(first,second,start));
            Future<ErrorCode> b=workers.submit(()->suspend(second,first,start));
            start.countDown();
            var outcomes=java.util.Arrays.asList(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(null,ErrorCode.LAST_ADMIN_CANNOT_BE_SUSPENDED);
            assertThat(jdbc.queryForObject("select count(*) from users where account_status='ACTIVE'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from auth_sessions where revoked_at is not null and revocation_reason='ADMIN_REVOKED'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from audit_logs where action='ADMIN_USER_SUSPENDED'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='ADMIN_USER_SUSPENDED'",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select after_snapshot::text from audit_logs where action='ADMIN_USER_SUSPENDED'",String.class))
                    .contains("AUDIT_TEST").doesNotContain("private note");
            UUID suspended=users.findById(first).orElseThrow().isActive()?second:first;
            UUID remaining=suspended.equals(first)?second:first;
            current.set(new CurrentActor(remaining,Set.of(SystemRole.ADMIN),UUID.randomUUID()));
            assertThatThrownBy(()->service.suspend(remaining,new AdminReasonRequest("AUDIT_TEST",null)))
                    .isInstanceOf(ApiException.class).extracting(e->((ApiException)e).code()).isEqualTo(ErrorCode.ADMIN_CANNOT_SUSPEND_SELF);
            var oldSession=sessions.findAllByUserIdOrderByCreatedAtDesc(suspended).getFirst();
            service.restore(suspended);
            assertThat(users.findById(suspended).orElseThrow().isActive()).isTrue();
            assertThat(sessions.findById(oldSession.getId()).orElseThrow().getRevokedAt()).isNotNull();
            assertThat(sessions.findUsableById(oldSession.getId())).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Long.class,suspended)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='ADMIN_USER_RESTORED' and aggregate_id=?",Long.class,suspended)).isEqualTo(1);
        } finally { current.remove(); workers.shutdownNow(); }
    }
    private UUID admin(Instant now) {
        UUID id=UUID.randomUUID(); users.saveAndFlush(UserEntity.active(id,"Safety admin",now));
        roles.saveAndFlush(SystemRoleAssignmentEntity.assign(UUID.randomUUID(),id,SystemRole.ADMIN,now));
        sessions.saveAndFlush(AuthSessionEntity.create(UUID.randomUUID(),id,UUID.randomUUID(),now,now.plusSeconds(3600)));
        return id;
    }
    private ErrorCode suspend(UUID actor,UUID target,CountDownLatch start) throws InterruptedException {
        start.await(); current.set(new CurrentActor(actor,Set.of(SystemRole.ADMIN),UUID.randomUUID()));
        try { service.suspend(target,new AdminReasonRequest("AUDIT_TEST","private note")); return null; }
        catch(ApiException exception) { return exception.code(); }
        finally { current.remove(); }
    }
}
