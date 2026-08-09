package vn.covie.auth.application;
import java.time.Instant; import java.util.UUID; import vn.covie.auth.model.AuthActionPurpose;
public record IssuedAuthActionTicket(String rawToken, AuthActionPurpose purpose, Instant expiresAt, UUID userId) {}
