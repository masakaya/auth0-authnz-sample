package com.example.authnz.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS configuration for the browser and mobile clients that call this API. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
