package com.fabops.telemetry.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fabops.telemetry")
public record TelemetryProperties(
		Topics topics,
		SchemaRegistry schemaRegistry,
		Ingestion ingestion
) {
	public record Topics(String raw, String dlq) {
	}

	public record SchemaRegistry(String url, String rawSubject, boolean classpathFallback) {
	}

	public record Ingestion(String serviceName, String defaultSchema, String defaultSchemaVersion) {
	}
}
