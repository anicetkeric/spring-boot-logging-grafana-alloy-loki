package com.bootlabs.logging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Binds all {@code logging.http.*} properties from application.yaml.
 * Registered via {@link LoggingConfig#enableConfigurationProperties}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "logging.http")
public class LoggingProperties {

    /**
     * Maximum number of bytes cached (and logged) for request/response bodies.
     * Applies to both {@link org.springframework.web.util.ContentCachingRequestWrapper}
     * and truncation of the logged string. Default: 10 000 characters.
     */
    private int maxBodySize = 10_000;

    /**
     * URI prefixes that bypass the logging filter entirely.
     * Matching uses {@link String#startsWith(String)}.
     */
    private Set<String> excludedPaths = Set.of("/actuator", "/health");

    /**
     * When {@code true}, request and response bodies are included in the log
     * event for loggable content-types (JSON, XML, text, form-encoded).
     * Bodies are always truncated to {@link #maxBodySize} characters.
     * Set to {@code false} (default) to keep log lines compact in production.
     */
    private boolean logBody = false;
}
