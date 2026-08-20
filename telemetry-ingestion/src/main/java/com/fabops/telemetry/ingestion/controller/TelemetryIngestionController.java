package com.fabops.telemetry.ingestion.controller;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import com.fabops.telemetry.ingestion.dto.IngestionResponse;
import com.fabops.telemetry.ingestion.dto.ValidationResult;
import com.fabops.telemetry.ingestion.exception.SchemaValidationException;
import com.fabops.telemetry.ingestion.service.TelemetryEnrichmentService;
import com.fabops.telemetry.ingestion.service.TelemetryProducer;
import com.fabops.telemetry.ingestion.service.TelemetryValidationService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryIngestionController {

	private final TelemetryValidationService validationService;
	private final TelemetryEnrichmentService enrichmentService;
	private final TelemetryProducer telemetryProducer;
	private final TelemetryProperties properties;

	public TelemetryIngestionController(
			TelemetryValidationService validationService,
			TelemetryEnrichmentService enrichmentService,
			TelemetryProducer telemetryProducer,
			TelemetryProperties properties
	) {
		this.validationService = validationService;
		this.enrichmentService = enrichmentService;
		this.telemetryProducer = telemetryProducer;
		this.properties = properties;
	}

	@PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<IngestionResponse> ingest(@RequestBody String payload) {
		ValidationResult result = validationService.validate(payload);

		if (!result.valid()) {
			String correlationId = extractCorrelationId(payload);
			throw new SchemaValidationException(
					result.errors(),
					payload,
					correlationId,
					result.schemaName(),
					result.schemaVersion()
			);
		}

		Instant ingestedAt = Instant.now();
		ObjectNode enriched = enrichmentService.enrich(result.record(), ingestedAt);
		String correlationId = enriched.get("correlationId").asText();
		String equipmentId = enriched.get("equipmentId").asText();

		try {
			telemetryProducer.sendValidated(equipmentId, enriched.toString()).get();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to publish validated telemetry to Kafka", ex);
		}

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(
				IngestionResponse.accepted(
						correlationId,
						properties.topics().raw(),
						result.schemaName(),
						result.schemaVersion(),
						ingestedAt
				)
		);
	}

	private String extractCorrelationId(String payload) {
		if (payload == null) {
			return "UNKNOWN";
		}
		int idx = payload.indexOf("\"correlationId\"");
		if (idx < 0) {
			return "UNKNOWN";
		}
		int colon = payload.indexOf(':', idx);
		int firstQuote = payload.indexOf('"', colon + 1);
		int secondQuote = payload.indexOf('"', firstQuote + 1);
		if (firstQuote < 0 || secondQuote < 0) {
			return "UNKNOWN";
		}
		return payload.substring(firstQuote + 1, secondQuote);
	}
}
