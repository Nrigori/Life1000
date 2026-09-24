package com.life1000.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("desktop")
public class DesktopCorsConfiguration {
    @Bean
    UrlBasedCorsConfigurationSource desktopCorsConfigurationSource() {
        var config = new CorsConfiguration();
        // 仅 Windows Tauri 的本地资源来源；预检放行不等于 API 免登录。
        config.setAllowedOrigins(List.of("http://tauri.localhost"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
