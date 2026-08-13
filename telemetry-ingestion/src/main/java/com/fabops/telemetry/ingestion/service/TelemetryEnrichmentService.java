package com.fabops.telemetry.ingestion.service;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Service;

/**
 * Enriches validated telemetry with ingestion metadata before Kafka publish.
 */
@Service
public class TelemetryEnrichmentService {

	private final TelemetryProperties properties;
	private final ObjectMapper objectMapper;

	public TelemetryEnrichmentService(TelemetryProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public ObjectNode enrich(GenericRecord record, Instant ingestedAt) {
		ObjectNode enriched = objectMapper.createObjectNode();

		enriched.put("equipmentId", stringValue(record.get("equipmentId")));
		enriched.put("timestamp", ((Number) record.get("timestamp")).longValue());
		enriched.set("payload", toJsonNode(record.get("payload")));
		enriched.put("correlationId", stringValue(record.get("correlationId")));
		enriched.put("schemaVersion", stringValue(record.get("schemaVersion")));

		Object sourceSystem = record.get("sourceSystem");
		enriched.put("sourceSystem", sourceSystem == null ? "unknown" : sourceSystem.toString());

		putOptionalString(enriched, "metricName", record.get("metricName"));
		putOptionalString(enriched, "unit", record.get("unit"));

		ObjectNode metadata = enriched.putObject("ingestionMetadata");
		metadata.put("ingestedAt", ingestedAt.toString());
		metadata.put("ingestionService", properties.ingestion().serviceName());
		metadata.put("ingestionId", UUID.randomUUID().toString());
		metadata.put("validated", true);

		return enriched;
	}

	private void putOptionalString(ObjectNode node, String field, Object value) {
		if (value == null || "null".equals(value.toString())) {
			node.putNull(field);
		}
		else {
			node.put(field, value.toString());
		}
	}

	private String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private JsonNode toJsonNode(Object value) {
		return objectMapper.valueToTree(value);
	}
}
