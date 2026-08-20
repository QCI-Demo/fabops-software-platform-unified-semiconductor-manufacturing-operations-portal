package com.fabops.telemetry.ingestion.service;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import com.fabops.telemetry.ingestion.dto.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.springframework.stereotype.Service;

/**
 * Validates inbound JSON telemetry payloads against Avro schemas using GenericDatumReader.
 */
@Service
public class TelemetryValidationService {

	private final SchemaLookupService schemaLookupService;
	private final TelemetryProperties properties;
	private final ObjectMapper objectMapper;

	public TelemetryValidationService(
			SchemaLookupService schemaLookupService,
			TelemetryProperties properties,
			ObjectMapper objectMapper
	) {
		this.schemaLookupService = schemaLookupService;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public ValidationResult validate(String jsonPayload) {
		String schemaName = properties.ingestion().defaultSchema();
		String schemaVersion = properties.ingestion().defaultSchemaVersion();
		List<String> errors = new ArrayList<>();

		if (jsonPayload == null || jsonPayload.isBlank()) {
			return ValidationResult.failure(List.of("Request body must not be empty"), schemaName, schemaVersion);
		}

		JsonNode root;
		try {
			root = objectMapper.readTree(jsonPayload);
		}
		catch (IOException ex) {
			return ValidationResult.failure(
					List.of("Invalid JSON: " + ex.getMessage()), schemaName, schemaVersion);
		}

		if (root.hasNonNull("schemaVersion")) {
			schemaVersion = root.get("schemaVersion").asText(schemaVersion);
		}

		Schema schema = schemaLookupService.resolveRawTelemetrySchema();
		schemaName = schema.getName();

		try {
			GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
			Decoder decoder = DecoderFactory.get().jsonDecoder(
					schema,
					new ByteArrayInputStream(jsonPayload.getBytes(StandardCharsets.UTF_8))
			);
			GenericRecord record = reader.read(null, decoder);
			List<String> semanticErrors = validateRequiredSemantics(record);
			if (!semanticErrors.isEmpty()) {
				return ValidationResult.failure(semanticErrors, schemaName, schemaVersion);
			}
			return ValidationResult.success(record, schemaName, schemaVersion);
		}
		catch (Exception ex) {
			errors.add(detailMessage(ex));
			Throwable cause = ex.getCause();
			while (cause != null && errors.size() < 5) {
				String detail = detailMessage(cause);
				if (!errors.contains(detail)) {
					errors.add(detail);
				}
				cause = cause.getCause();
			}
			return ValidationResult.failure(errors, schemaName, schemaVersion);
		}
	}

	private List<String> validateRequiredSemantics(GenericRecord record) {
		List<String> errors = new ArrayList<>();
		requireNonBlank(record, "equipmentId", errors);
		requireNonBlank(record, "correlationId", errors);
		requireNonBlank(record, "schemaVersion", errors);
		Object timestamp = record.get("timestamp");
		if (timestamp == null) {
			errors.add("Field 'timestamp' is required");
		}
		Object payload = record.get("payload");
		if (payload == null) {
			errors.add("Field 'payload' is required");
		}
		return errors;
	}

	private void requireNonBlank(GenericRecord record, String field, List<String> errors) {
		Object value = record.get(field);
		if (value == null || value.toString().isBlank()) {
			errors.add("Field '" + field + "' is required and must be non-blank");
		}
	}

	private String detailMessage(Throwable ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}
		return ex.getClass().getSimpleName() + ": " + message;
	}
}
