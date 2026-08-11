package vn.covie.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfiguration {
    @Bean
    @Profile("!test")
    JwtKeyPair configuredJwtKeyPair(AuthProperties properties) throws Exception {
        if (properties.jwt().privateKeyPath() == null || properties.jwt().publicKeyPath() == null) {
            throw new IllegalStateException("COVIE_JWT_PRIVATE_KEY_PATH and COVIE_JWT_PUBLIC_KEY_PATH must be configured");
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(properties.jwt().privateKeyPath())));
        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(readPem(properties.jwt().publicKeyPath())));
        return new JwtKeyPair(privateKey, publicKey);
    }

    @Bean
    @Profile("test")
    JwtKeyPair testJwtKeyPair() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        return new JwtKeyPair((RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyPair pair, AuthProperties properties) {
        RSAKey key = new RSAKey.Builder(pair.publicKey()).privateKey(pair.privateKey()).keyID(properties.jwt().keyId()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyPair pair, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(pair.publicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static byte[] readPem(java.nio.file.Path path) throws Exception {
        String pem = Files.readString(path).replaceAll("-----BEGIN [A-Z ]+-----", "").replaceAll("-----END [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    record JwtKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey) {}
}
