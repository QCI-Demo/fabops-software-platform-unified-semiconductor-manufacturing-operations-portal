package com.fabops.equipment.processor.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents raw telemetry data consumed from the telemetry topic.
 * Structure mirrors the RawTelemetryEvent schema from the ingestion service.
 */
public record Telemetry(
		String correlationId,
		String equipmentId,
		String equipmentType,
		Instant timestamp,
		Double temperature,
		Double pressure,
		List<String> errorCodes,
		Map<String, Object> metadata,
		String schemaVersion
) {
	/**
	 * Creates a Telemetry instance with default values for optional fields.
	 */
	public static Telemetry of(
			String correlationId,
			String equipmentId,
			String equipmentType,
			Instant timestamp,
			Double temperature,
			Double pressure,
			List<String> errorCodes
	) {
		return new Telemetry(
				correlationId,
				equipmentId,
				equipmentType,
				timestamp,
				temperature,
				pressure,
				errorCodes != null ? errorCodes : List.of(),
				Map.of(),
				"1.0.0"
		);
	}
}
