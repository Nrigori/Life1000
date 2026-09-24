package com.life1000.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {
    public static final String ISSUER = "life1000";
    public static final String AUDIENCE = "life1000-api";

    @Bean
    SecretKey jwtKey(AuthProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.jwtSecret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("LIFE1000_JWT_SECRET must be Base64");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("LIFE1000_JWT_SECRET must contain at least 32 random bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    // 验签之外还校验签发方、接收方和当前单用户账号，避免接受用途或身份不符的 Token。
    @Bean
    JwtDecoder jwtDecoder(SecretKey key, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<String>("sub", properties.username()::equals),
                new JwtClaimValidator<List<String>>("aud",
                        audiences -> audiences != null && audiences.contains(AUDIENCE)),
                new JwtClaimValidator<Object>("exp", value -> value != null)));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    // 使用显式 Bearer 请求头而非 Cookie 会话；禁用 CSRF 依赖这个前提。
    // 除登录外，健康检查与附件读取同样要求 JWT；静态页面由 Nginx 提供。
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            org.springframework.beans.factory.ObjectProvider<DesktopCorsConfiguration> desktopCors) throws Exception {
        if (desktopCors.getIfAvailable() != null) {
            http.cors(cors -> cors.configurationSource(desktopCors.getObject().desktopCorsConfigurationSource()));
        }
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {})
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录或重新登录\"}");
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"请先登录或重新登录\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"不允许访问\"}");
                        }))
                .build();
    }
}
