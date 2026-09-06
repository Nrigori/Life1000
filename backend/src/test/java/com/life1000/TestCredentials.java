package com.life1000;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestCredentials {
    public static final String USERNAME = "phase1-test";
    public static final String PASSWORD = UUID.randomUUID().toString();
    public static final String SECRET;
    static {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        SECRET = Base64.getEncoder().encodeToString(bytes);
    }

    private TestCredentials() {}

    public static void configure(DynamicPropertyRegistry registry) {
        registry.add("life1000.auth.username", () -> USERNAME);
        registry.add("life1000.auth.password", () -> PASSWORD);
        registry.add("life1000.auth.jwt-secret", () -> SECRET);
        registry.add("life1000.auth.token-ttl-seconds", () -> 7200);
    }
}
