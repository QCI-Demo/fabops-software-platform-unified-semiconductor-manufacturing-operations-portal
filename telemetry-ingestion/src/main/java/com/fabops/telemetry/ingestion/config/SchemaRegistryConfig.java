package com.fabops.telemetry.ingestion.config;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchemaRegistryConfig {

	@Bean
	public SchemaRegistryClient schemaRegistryClient(TelemetryProperties properties) {
		return new CachedSchemaRegistryClient(properties.schemaRegistry().url(), 100);
	}
}
