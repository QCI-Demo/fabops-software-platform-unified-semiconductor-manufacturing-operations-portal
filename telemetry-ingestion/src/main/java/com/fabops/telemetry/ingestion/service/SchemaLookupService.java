package com.fabops.telemetry.ingestion.service;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Loads Avro schemas from Confluent Schema Registry with optional classpath fallback
 * that mirrors the FabOps telemetry schema registry contracts.
 */
@Service
public class SchemaLookupService {

	private static final Logger log = LoggerFactory.getLogger(SchemaLookupService.class);

	private final SchemaRegistryClient schemaRegistryClient;
	private final TelemetryProperties properties;
	private final Map<String, Schema> classpathSchemas = new ConcurrentHashMap<>();

	public SchemaLookupService(SchemaRegistryClient schemaRegistryClient, TelemetryProperties properties) {
		this.schemaRegistryClient = schemaRegistryClient;
		this.properties = properties;
		if (properties.schemaRegistry().classpathFallback()) {
			loadClasspathSchemas();
		}
	}

	public Schema resolveRawTelemetrySchema() {
		String subject = properties.schemaRegistry().rawSubject();
		try {
			var metadata = schemaRegistryClient.getLatestSchemaMetadata(subject);
			return new Schema.Parser().parse(metadata.getSchema());
		}
		catch (IOException | RestClientException ex) {
			log.warn("Schema Registry unavailable for subject {}; using classpath fallback: {}",
					subject, ex.getMessage());
			Schema fallback = classpathSchemas.get("RawTelemetryEvent");
			if (fallback == null) {
				throw new IllegalStateException(
						"Unable to load RawTelemetryEvent schema from registry or classpath", ex);
			}
			return fallback;
		}
	}

	public Schema resolveByName(String schemaName) {
		Schema schema = classpathSchemas.get(schemaName);
		if (schema != null) {
			return schema;
		}
		return resolveRawTelemetrySchema();
	}

	private void loadClasspathSchemas() {
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
			Resource[] resources = resolver.getResources("classpath:schemas/*.avsc");
			Schema.Parser parser = new Schema.Parser();
			for (Resource resource : resources) {
				try (InputStream in = resource.getInputStream()) {
					String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
					Schema schema = parser.parse(content);
					classpathSchemas.put(schema.getName(), schema);
					log.info("Loaded classpath schema {} from {}", schema.getName(), resource.getFilename());
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load classpath Avro schemas", ex);
		}
	}
}
