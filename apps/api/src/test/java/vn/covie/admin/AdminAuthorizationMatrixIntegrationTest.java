package vn.covie.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class AdminAuthorizationMatrixIntegrationTest {
    static Stream<Set<SystemRole>> nonAdminRoles() { return Stream.of(Set.of(), Set.of(SystemRole.USER)); }

    @ParameterizedTest @MethodSource("nonAdminRoles")
    void nonAdminGlobalRolesAreRejected(Set<SystemRole> roles) {
        CurrentActorProvider actors = Mockito.mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(UUID.randomUUID(), roles, UUID.randomUUID()));
        assertThatThrownBy(() -> new AdminAccessPolicy(actors).requireAdmin()).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.ADMIN_ROLE_REQUIRED);
    }

    @ParameterizedTest @MethodSource("adminRoles")
    void globalAdminIsAcceptedWithoutAnyMerchantMembership(Set<SystemRole> roles) {
        CurrentActor actor = new CurrentActor(UUID.randomUUID(), roles, UUID.randomUUID());
        CurrentActorProvider actors = Mockito.mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(actor);
        assertThat(new AdminAccessPolicy(actors).requireAdmin()).isEqualTo(actor);
    }
    static Stream<Set<SystemRole>> adminRoles() { return Stream.of(Set.of(SystemRole.ADMIN), Set.of(SystemRole.USER, SystemRole.ADMIN)); }
}
