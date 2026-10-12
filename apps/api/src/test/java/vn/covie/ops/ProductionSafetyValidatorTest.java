package vn.covie.ops;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import vn.covie.auth.security.AuthProperties;

class ProductionSafetyValidatorTest {
    @TempDir Path directory;
    private MockEnvironment environment() {
        var env=new MockEnvironment().withProperty("spring.jpa.hibernate.ddl-auto","validate")
                .withProperty("spring.flyway.enabled","true")
                .withProperty("spring.security.oauth2.client.registration.google.client-id","test.apps.googleusercontent.com")
                .withProperty("spring.security.oauth2.client.registration.google.client-secret","test-only-provider-secret")
                .withProperty("spring.security.oauth2.client.registration.google.redirect-uri","https://covie.test/api/v1/auth/google/callback/google");
        env.setActiveProfiles("prod"); return env;
    }
    private AuthProperties auth(boolean secure) throws Exception {
        var privateFile=directory.resolve("private.pem"); var publicFile=directory.resolve("public.pem");
        Files.writeString(privateFile,"readability-fixture"); Files.writeString(publicFile,"readability-fixture");
        return new AuthProperties("https://covie.test",Duration.ofMinutes(15),Duration.ofDays(30),Duration.ofMinutes(10),
                new AuthProperties.Jwt("test",privateFile,publicFile),new AuthProperties.Cookies(secure,"Lax","covie_at","covie_rt","covie_auth_action"));
    }
    @Test void acceptsConfiguredProductionSafetyBaseline() throws Exception {
        var validator=new ProductionSafetyValidator(new ProductionSafetyProperties(URI.create("https://covie.test"),false),auth(true),environment());
        assertThatCode(()->validator.run(null)).doesNotThrowAnyException();
    }
    @ParameterizedTest
    @CsvSource({"spring.jpa.hibernate.ddl-auto,update","spring.flyway.enabled,false",
            "spring.security.oauth2.client.registration.google.client-id,disabled-local-client",
            "spring.security.oauth2.client.registration.google.client-secret,disabled-local-secret",
            "spring.security.oauth2.client.registration.google.redirect-uri,https://foreign.test/api/v1/auth/google/callback/google"})
    void rejectsUnsafeEnvironmentWithoutLeakingItsValue(String key,String value) throws Exception {
        var validator=new ProductionSafetyValidator(new ProductionSafetyProperties(URI.create("https://covie.test"),false),auth(true),environment().withProperty(key,value));
        assertThatThrownBy(()->validator.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsafe production configuration: "+key);
    }
    @Test void rejectsHttpPublicUrlInvalidIssuerAndMissingPublicKey() throws Exception {
        var valid=auth(true);
        var properties=new ProductionSafetyProperties(URI.create("https://covie.test"),false);
        assertThatThrownBy(()->new ProductionSafetyValidator(new ProductionSafetyProperties(URI.create("http://covie.test"),false),valid,environment()).run(null))
                .hasMessage("Unsafe production configuration: covie.production.public-url");
        for(String issuer:java.util.List.of("http://covie.test","https:///missing-host","invalid uri")) {
            var invalid=new AuthProperties(issuer,valid.accessTokenTtl(),valid.refreshTokenTtl(),valid.actionTicketTtl(),valid.jwt(),valid.cookies());
            assertThatThrownBy(()->new ProductionSafetyValidator(properties,invalid,environment()).run(null))
                    .hasMessage("Unsafe production configuration: covie.auth.issuer");
        }
        Files.delete(valid.jwt().publicKeyPath());
        assertThatThrownBy(()->new ProductionSafetyValidator(properties,valid,environment()).run(null))
                .hasMessage("Unsafe production configuration: covie.auth.jwt.public-key-path");
    }

    @Test void rejectsInsecureCookieE2eAndMissingKeyFiles() throws Exception {
        var properties=new ProductionSafetyProperties(URI.create("https://covie.test"),false);
        var secure=auth(true);
        assertThatThrownBy(()->new ProductionSafetyValidator(properties,auth(false),environment()).run(null))
                .hasMessage("Unsafe production configuration: covie.auth.cookies.secure");
        var e2e=environment(); e2e.setActiveProfiles("prod","e2e");
        assertThatThrownBy(()->new ProductionSafetyValidator(properties,secure,e2e).run(null))
                .hasMessage("Unsafe production configuration: spring.profiles.active");
        assertThatThrownBy(()->new ProductionSafetyValidator(new ProductionSafetyProperties(properties.publicUrl(),true),secure,environment()).run(null))
                .hasMessage("Unsafe production configuration: covie.production.e2e-support-enabled");
        Files.delete(secure.jwt().privateKeyPath());
        assertThatThrownBy(()->new ProductionSafetyValidator(properties,secure,environment()).run(null))
                .hasMessage("Unsafe production configuration: covie.auth.jwt.private-key-path");
    }
}
