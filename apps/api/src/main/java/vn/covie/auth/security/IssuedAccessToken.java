package vn.covie.auth.security;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {}
