package com.life1000.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("life1000.auth")
public record AuthProperties(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String jwtSecret,
        @Min(60) @Max(86400) long tokenTtlSeconds) {
    @Override
    public String toString() {
        return "AuthProperties[credentials=REDACTED]";
    }
}
