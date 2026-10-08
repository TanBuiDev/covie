package vn.covie.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import vn.covie.auth.api.GoogleAuthController;
import vn.covie.shared.web.ApiException;

class GoogleOAuthAvailabilityTest {
    @Test void rejectsMissingRegistration() {
        assertThat(new GoogleOAuthAvailability(id -> null).configured()).isFalse();
    }

    @Test void rejectsDisabledLocalPlaceholdersWithoutCreatingAnOAuthSession() {
        var availability = availability("disabled-local-client", "disabled-local-secret");
        assertThat(availability.configured()).isFalse();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new GoogleAuthController(availability).start(GoogleAuthIntent.LOGIN,request,response))
                .isInstanceOf(ApiException.class);
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test void reportsOnlyConfiguredStateAndPreservesAConfiguredStart() throws Exception {
        var availability = availability("123.apps.googleusercontent.com", "test-configured-secret");
        assertThat(availability.configured()).isTrue();
        var controller = new GoogleAuthController(availability);
        assertThat(controller.status().configured()).isTrue();
        assertThat(controller.status().toString()).doesNotContain("123", "secret");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        controller.start(GoogleAuthIntent.PASSWORD_RESET,request,response);
        assertThat(request.getSession(false).getAttribute(GoogleAuthController.SESSION_INTENT)).isEqualTo("PASSWORD_RESET");
        assertThat(response.getRedirectedUrl()).isEqualTo("/api/v1/auth/oauth2/authorization/google");
    }

    private GoogleOAuthAvailability availability(String clientId, String secret) {
        var registration = ClientRegistration.withRegistrationId("google").clientId(clientId).clientSecret(secret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback").authorizationUri("https://example.invalid/authorize")
                .tokenUri("https://example.invalid/token").build();
        return new GoogleOAuthAvailability(id -> registration);
    }
}
