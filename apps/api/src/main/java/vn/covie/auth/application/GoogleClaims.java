package vn.covie.auth.application;

public record GoogleClaims(String subject, String email, Boolean emailVerified, String displayName) {}
