package com.fabops.stream.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Telemetry event from equipment sensors.
 * Immutable record supporting schema evolution with optional fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Telemetry(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("equipmentId") String equipmentId,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("pressure") Double pressure,
    @JsonProperty("errorCodes") List<String> errorCodes,
    @JsonProperty("schemaVersion") Integer schemaVersion
) {
    /**
     * Creates a telemetry instance with defaults for missing fields.
     */
    public Telemetry {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (errorCodes == null) {
            errorCodes = List.of();
        }
        if (schemaVersion == null) {
            schemaVersion = 1;
        }
    }

    /**
     * Convenience constructor for testing.
     */
    public static Telemetry of(String equipmentId, double temperature, double pressure, List<String> errorCodes) {
        return new Telemetry(
            UUID.randomUUID().toString(),
            equipmentId,
            UUID.randomUUID().toString(),
            Instant.now(),
            temperature,
            pressure,
            errorCodes,
            1
        );
    }
}
