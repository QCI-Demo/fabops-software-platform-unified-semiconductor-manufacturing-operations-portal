package com.fabops.telemetry.ingestion.service;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Publishes validated telemetry to the raw topic and failed payloads to the dead-letter topic.
 */
@Service
public class TelemetryProducer {

	private static final Logger log = LoggerFactory.getLogger(TelemetryProducer.class);

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final TelemetryProperties properties;
	private final ObjectMapper objectMapper;

	public TelemetryProducer(
			KafkaTemplate<String, String> kafkaTemplate,
			TelemetryProperties properties,
			ObjectMapper objectMapper
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public CompletableFuture<SendResult<String, String>> sendValidated(String key, String enrichedJson) {
		String topic = properties.topics().raw();
		log.info("Publishing validated telemetry to topic={} key={}", topic, key);
		return kafkaTemplate.send(topic, key, enrichedJson);
	}

	public CompletableFuture<SendResult<String, String>> sendToDeadLetter(
			String originalPayload,
			List<String> errors,
			String correlationId,
			String schemaName,
			String schemaVersion
	) {
		String topic = properties.topics().dlq();
		String dlqMessage = buildDeadLetterMessage(
				originalPayload, errors, correlationId, schemaName, schemaVersion);
		String key = correlationId != null && !correlationId.isBlank() ? correlationId : "UNKNOWN";
		log.warn("Publishing invalid telemetry to DLQ topic={} key={} errors={}", topic, key, errors);
		return kafkaTemplate.send(topic, key, dlqMessage);
	}

	private String buildDeadLetterMessage(
			String originalPayload,
			List<String> errors,
			String correlationId,
			String schemaName,
			String schemaVersion
	) {
		try {
			ObjectNode root = objectMapper.createObjectNode();
			root.put("equipmentId", extractEquipmentId(originalPayload));
			root.put("timestamp", Instant.now().toEpochMilli());
			root.put("correlationId", correlationId == null || correlationId.isBlank() ? "UNKNOWN" : correlationId);
			root.put("schemaVersion", "1.0.0");

			ObjectNode payload = root.putObject("payload");
			payload.put("originalTopic", properties.topics().raw());
			payload.put("originalSchema", schemaName);
			payload.put("originalSchemaVersion", schemaVersion);
			payload.put("failureReason", String.join("; ", errors));
			payload.put("failureCode", "SCHEMA_VALIDATION");
			payload.put("originalPayload", originalPayload == null ? "" : originalPayload);
			payload.put("attemptCount", 1);

			ObjectNode errorMetadata = root.putObject("errorMetadata");
			errorMetadata.put("source", properties.ingestion().serviceName());
			errorMetadata.putPOJO("errors", errors);
			errorMetadata.put("deadLetteredAt", Instant.now().toString());

			return objectMapper.writeValueAsString(root);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize dead-letter message", ex);
		}
	}

	private String extractEquipmentId(String originalPayload) {
		if (originalPayload == null || originalPayload.isBlank()) {
			return "UNKNOWN";
		}
		try {
			var node = objectMapper.readTree(originalPayload);
			if (node.hasNonNull("equipmentId")) {
				return node.get("equipmentId").asText("UNKNOWN");
			}
		}
		catch (Exception ignored) {
			// Fall through to UNKNOWN
		}
		return "UNKNOWN";
	}
}
