package com.arayas.transaction.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spin.provider")
public record ProviderProperties(
		String baseUrl,
		int connectTimeoutMs,
		int readTimeoutMs) {
}
