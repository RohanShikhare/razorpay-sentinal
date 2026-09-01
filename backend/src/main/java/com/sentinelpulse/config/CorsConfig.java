package com.sentinelpulse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for local development.
 *
 * Allowed origins are driven by the {@code SENTINEL_CORS_ALLOWED_ORIGINS}
 * environment variable (comma-separated), defaulting to the standard
 * Next.js dev server at http://localhost:3000.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${sentinel.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
