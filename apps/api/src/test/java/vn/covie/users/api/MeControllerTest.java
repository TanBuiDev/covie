package vn.covie.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.users.api.dto.MeResponse;
import vn.covie.users.api.dto.UpdateMeRequest;
import vn.covie.users.application.UserProfileService;

class MeControllerTest {
    @Test
    void returnsOnlyTheAuthenticatedUsersReadModelAndUpdatesDisplayName() {
        UserProfileService profiles = mock(UserProfileService.class);
        MeResponse expected = new MeResponse(UUID.randomUUID(), "Minh", "minh@example.com", "ACTIVE", 0, List.of("USER"));
        when(profiles.me()).thenReturn(expected);
        var request=new UpdateMeRequest("Minh Nguyen");
        when(profiles.update(request)).thenReturn(expected);
        MeController controller = new MeController(profiles);

        assertThat(controller.me()).isEqualTo(expected);
        assertThat(controller.update(request).getBody()).isEqualTo(expected);
        verify(profiles).update(request);
    }
}
