package com.fabops.telemetry.ingestion.dto;

import java.time.Instant;
import java.util.List;

public record IngestionResponse(
		String status,
		String correlationId,
		String topic,
		String schemaName,
		String schemaVersion,
		Instant ingestedAt,
		List<String> errors
) {
	public static IngestionResponse accepted(
			String correlationId,
			String topic,
			String schemaName,
			String schemaVersion,
			Instant ingestedAt
	) {
		return new IngestionResponse("ACCEPTED", correlationId, topic, schemaName, schemaVersion, ingestedAt, List.of());
	}

	public static IngestionResponse rejected(String correlationId, List<String> errors) {
		return new IngestionResponse("REJECTED", correlationId, null, null, null, Instant.now(), errors);
	}
}
