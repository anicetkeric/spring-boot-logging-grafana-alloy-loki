package com.bootlabs.logging.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LoggingProperties} as a Spring-managed bean so it can be
 * injected into {@link com.bootlabs.logging.filter.HttpLoggingFilter}.
 */
@Configuration
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingConfig {
}
