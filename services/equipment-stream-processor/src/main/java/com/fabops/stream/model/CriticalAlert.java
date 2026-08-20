package com.fabops.stream.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Critical alert event emitted when equipment health reaches critical threshold.
 * Versioned record preserving correlation identifiers for traceability.
 */
public record CriticalAlert(
    @JsonProperty("alertId") String alertId,
    @JsonProperty("equipmentId") String equipmentId,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("alertType") AlertType alertType,
    @JsonProperty("healthScore") int healthScore,
    @JsonProperty("message") String message,
    @JsonProperty("temperatureValue") Double temperatureValue,
    @JsonProperty("pressureValue") Double pressureValue,
    @JsonProperty("errorCodes") java.util.List<String> errorCodes,
    @JsonProperty("sourceEventId") String sourceEventId,
    @JsonProperty("schemaVersion") int schemaVersion
) {
    
    public enum AlertType {
        TEMPERATURE_CRITICAL,
        PRESSURE_CRITICAL,
        ERROR_CODE_CRITICAL,
        COMBINED_CRITICAL
    }

    /**
     * Creates a critical alert from equipment health and telemetry data.
     */
    public static CriticalAlert fromHealthAndTelemetry(
            EquipmentHealth health, 
            Telemetry telemetry,
            AlertType alertType,
            String message) {
        return new CriticalAlert(
            java.util.UUID.randomUUID().toString(),
            health.equipmentId(),
            health.correlationId(),
            Instant.now(),
            alertType,
            health.healthScore(),
            message,
            telemetry.temperature(),
            telemetry.pressure(),
            telemetry.errorCodes(),
            telemetry.eventId(),
            1
        );
    }
}
