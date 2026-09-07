package com.life1000.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import com.life1000.common.ApiException;

@Service
public class AuthService {
    private final AuthProperties properties;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String passwordHash;

    // 单用户身份来自私有配置，不建立注册或用户表；启动时生成内存密码摘要用于后续校验。
    public AuthService(AuthProperties properties, PasswordEncoder passwords, JwtEncoder encoder) {
        this.properties = properties;
        this.passwords = passwords;
        this.encoder = encoder;
        this.passwordHash = passwords.encode(properties.password());
    }

    public TokenResponse login(String username, String password) {
        // 用户名错误时也执行密码校验，减少通过响应耗时区分账号是否正确的机会。
        // Always perform the password check, even when the username is wrong.
        boolean passwordMatches = passwords.matches(password, passwordHash);
        boolean usernameMatches = MessageDigest.isEqual(
                properties.username().getBytes(StandardCharsets.UTF_8),
                username.getBytes(StandardCharsets.UTF_8));
        if (!passwordMatches || !usernameMatches) {
            throw ApiException.unauthorized("账号或密码错误");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfiguration.ISSUER)
                .subject(properties.username())
                .audience(List.of(SecurityConfiguration.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.tokenTtlSeconds()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.tokenTtlSeconds());
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
