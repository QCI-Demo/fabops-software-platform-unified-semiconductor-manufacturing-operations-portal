package com.fabops.telemetry.ingestion.exception;

import java.util.List;

public class SchemaValidationException extends RuntimeException {

	private final List<String> errors;
	private final String originalPayload;
	private final String correlationId;
	private final String schemaName;
	private final String schemaVersion;

	public SchemaValidationException(
			List<String> errors,
			String originalPayload,
			String correlationId,
			String schemaName,
			String schemaVersion
	) {
		super(String.join("; ", errors));
		this.errors = List.copyOf(errors);
		this.originalPayload = originalPayload;
		this.correlationId = correlationId;
		this.schemaName = schemaName;
		this.schemaVersion = schemaVersion;
	}

	public List<String> getErrors() {
		return errors;
	}

	public String getOriginalPayload() {
		return originalPayload;
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public String getSchemaName() {
		return schemaName;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}
}
