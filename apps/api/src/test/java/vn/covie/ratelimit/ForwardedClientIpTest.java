package vn.covie.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

class ForwardedClientIpTest {
    @Test
    void unsanitizedStandardForwardedHeaderOverridesTrustedLegacyHeader() throws Exception {
        var request = proxyRequest();
        request.addHeader("Forwarded", "for=203.0.113.99;proto=http;host=attacker.example");
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {
            var forwarded = (jakarta.servlet.http.HttpServletRequest) incoming;
            assertThat(new ClientIpResolver().resolve(forwarded)).isEqualTo("203.0.113.99");
            assertThat(forwarded.getServerName()).isEqualTo("attacker.example");
            assertThat(forwarded.getScheme()).isEqualTo("http");
        });
    }

    @Test
    void clearingStandardHeaderPreservesProxySuppliedClientAddressHostAndScheme() throws Exception {
        new ForwardedHeaderFilter().doFilter(proxyRequest(), new MockHttpServletResponse(), (incoming, outgoing) -> {
            var forwarded = (jakarta.servlet.http.HttpServletRequest) incoming;
            assertThat(new ClientIpResolver().resolve(forwarded)).isEqualTo("198.51.100.10");
            assertThat(forwarded.getServerName()).isEqualTo("covie.vn");
            assertThat(forwarded.getScheme()).isEqualTo("https");
        });
    }

    private MockHttpServletRequest proxyRequest() {
        var request = new MockHttpServletRequest("GET", "/api/v1/categories");
        request.setRemoteAddr("172.18.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.10");
        request.addHeader("X-Forwarded-Host", "covie.vn");
        request.addHeader("X-Forwarded-Proto", "https");
        return request;
    }
}
