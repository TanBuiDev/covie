package vn.covie.admin.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class AdminAccessPolicyTest {
    @Test void rejectsActorWithoutAdminRole() {
        CurrentActorProvider actors = Mockito.mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(UUID.randomUUID(), Set.of(SystemRole.USER), UUID.randomUUID()));
        assertThatThrownBy(() -> new AdminAccessPolicy(actors).requireAdmin()).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code()).isEqualTo(ErrorCode.ADMIN_ROLE_REQUIRED);
    }
}
