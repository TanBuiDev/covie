package vn.covie.auth.application;
import java.time.Instant; import java.util.UUID; import vn.covie.auth.security.IssuedAccessToken;
public record IssuedSession(UUID sessionId,String rawRefreshToken,Instant refreshExpiresAt,IssuedAccessToken accessToken) {}
