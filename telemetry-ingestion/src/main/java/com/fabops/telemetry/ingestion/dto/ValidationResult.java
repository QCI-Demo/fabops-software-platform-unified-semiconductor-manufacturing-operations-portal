package com.fabops.telemetry.ingestion.dto;

import java.util.List;
import org.apache.avro.generic.GenericRecord;

public record ValidationResult(
		boolean valid,
		GenericRecord record,
		List<String> errors,
		String schemaName,
		String schemaVersion
) {
	public static ValidationResult success(GenericRecord record, String schemaName, String schemaVersion) {
		return new ValidationResult(true, record, List.of(), schemaName, schemaVersion);
	}

	public static ValidationResult failure(List<String> errors, String schemaName, String schemaVersion) {
		return new ValidationResult(false, null, List.copyOf(errors), schemaName, schemaVersion);
	}
}
