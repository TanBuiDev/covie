package vn.covie.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
public class AuthSecurityConfiguration {
    @Bean CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository repository=CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("COVIE-XSRF-TOKEN"); repository.setHeaderName("X-CSRF-Token"); repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(properties.cookies().secure())); return repository;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrf, BrowserCsrfRequestMatcher csrfMatcher, BrowserCookieCsrfFilter browserCsrf, CookieOrHeaderBearerTokenResolver resolver, ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler denied, ActiveSessionFilter active, vn.covie.auth.oauth.GoogleOAuthSuccessHandler googleOAuthSuccessHandler, vn.covie.auth.oauth.GoogleOAuthFailureHandler googleOAuthFailureHandler) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET,"/api/v1/health","/api/v1/auth/csrf","/api/v1/auth/google/status","/api/v1/auth/google/start").permitAll()
            .requestMatchers(HttpMethod.GET,"/api/v1/media/*/content").permitAll()
            .requestMatchers(HttpMethod.HEAD,"/api/v1/media/*/content").permitAll()
            .requestMatchers(HttpMethod.GET,
                "/api/v1/location-areas",
                "/api/v1/ads/active",
                "/api/v1/billing/plans",
                "/api/v1/categories",
                "/api/v1/categories/**",
                "/api/v1/catalog/**",
                "/api/v1/barcodes/**",
                "/api/v1/merchants/*/reviews").permitAll()
            .requestMatchers("/api/v1/auth/google/callback/**","/api/v1/auth/oauth2/authorization/**").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/v1/ads/*/events","/api/v1/billing/webhooks/payos").permitAll()
            .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/logout","/api/v1/auth/onboarding/set-password","/api/v1/auth/password/reset-with-google").permitAll()
            .requestMatchers("/api/v1/**").authenticated().anyRequest().permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .oauth2Login(oauth -> oauth.authorizationEndpoint(authorization -> authorization.baseUri("/api/v1/auth/oauth2/authorization")).redirectionEndpoint(redirection -> redirection.baseUri("/api/v1/auth/google/callback/*")).successHandler(googleOAuthSuccessHandler).failureHandler(googleOAuthFailureHandler))
            .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(resolver).jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
            .csrf(csrfConfig -> csrfConfig.csrfTokenRepository(csrf).requireCsrfProtectionMatcher(csrfMatcher).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied));
        http.addFilterBefore(browserCsrf, BearerTokenAuthenticationFilter.class); http.addFilterAfter(active, BearerTokenAuthenticationFilter.class); return http.build();
    }
}
