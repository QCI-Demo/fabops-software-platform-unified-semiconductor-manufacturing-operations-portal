package com.fabops.equipment.processor.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents the computed equipment health status.
 * Contains health score, criticality flag, and detailed breakdown.
 */
public record EquipmentHealth(
		String correlationId,
		String equipmentId,
		String equipmentType,
		Instant telemetryTimestamp,
		Instant processedAt,
		int healthScore,
		boolean isCritical,
		HealthSeverity severity,
		HealthBreakdown breakdown,
		String schemaVersion
) {
	/**
	 * Health severity levels for branching logic.
	 */
	public enum HealthSeverity {
		HEALTHY,
		WARNING,
		CRITICAL
	}

	/**
	 * Detailed breakdown of health score components.
	 */
	public record HealthBreakdown(
			int temperatureScore,
			int pressureScore,
			int errorCodeScore,
			List<String> violations
	) {}

	/**
	 * Builder for EquipmentHealth instances.
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String correlationId;
		private String equipmentId;
		private String equipmentType;
		private Instant telemetryTimestamp;
		private int healthScore = 100;
		private boolean isCritical = false;
		private HealthSeverity severity = HealthSeverity.HEALTHY;
		private HealthBreakdown breakdown;

		public Builder correlationId(String correlationId) {
			this.correlationId = correlationId;
			return this;
		}

		public Builder equipmentId(String equipmentId) {
			this.equipmentId = equipmentId;
			return this;
		}

		public Builder equipmentType(String equipmentType) {
			this.equipmentType = equipmentType;
			return this;
		}

		public Builder telemetryTimestamp(Instant telemetryTimestamp) {
			this.telemetryTimestamp = telemetryTimestamp;
			return this;
		}

		public Builder healthScore(int healthScore) {
			this.healthScore = healthScore;
			return this;
		}

		public Builder isCritical(boolean isCritical) {
			this.isCritical = isCritical;
			return this;
		}

		public Builder severity(HealthSeverity severity) {
			this.severity = severity;
			return this;
		}

		public Builder breakdown(HealthBreakdown breakdown) {
			this.breakdown = breakdown;
			return this;
		}

		public EquipmentHealth build() {
			return new EquipmentHealth(
					correlationId,
					equipmentId,
					equipmentType,
					telemetryTimestamp,
					Instant.now(),
					healthScore,
					isCritical,
					severity,
					breakdown,
					"1.0.0"
			);
		}
	}
}
