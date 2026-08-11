package vn.covie.auth.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final AppClock clock;
    private final UuidV7Generator ids;

    public JwtTokenService(JwtEncoder encoder, AuthProperties properties, AppClock clock, UuidV7Generator ids) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.ids = ids;
    }

    public IssuedAccessToken issueAccessToken(UUID userId, UUID sessionId, Set<SystemRole> roles) {
        Instant now = clock.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(ids.generate().toString())
                .claim("sessionId", sessionId.toString())
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.jwt().keyId()).build(), claims));
        return new IssuedAccessToken(jwt.getTokenValue(), expiresAt);
    }
}
