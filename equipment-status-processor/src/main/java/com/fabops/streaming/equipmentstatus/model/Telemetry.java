package com.fabops.streaming.equipmentstatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Raw telemetry event from equipment sensors.
 * Maps to the RawTelemetryEvent Avro schema.
 */
public record Telemetry(
    @JsonProperty("equipmentId") String equipmentId,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("payload") Map<String, String> payload,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("schemaVersion") String schemaVersion,
    @JsonProperty("sourceSystem") String sourceSystem,
    @JsonProperty("metricName") String metricName,
    @JsonProperty("unit") String unit
) {
    /**
     * Extract a numeric value from payload by key.
     * Returns null if the key is missing or value is not parseable.
     */
    public Double getNumericValue(String key) {
        if (payload == null || !payload.containsKey(key)) {
            return null;
        }
        try {
            return Double.parseDouble(payload.get(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Check if the payload contains a specific error code.
     */
    public boolean hasErrorCode(String errorCode) {
        if (payload == null) {
            return false;
        }
        String code = payload.get("errorCode");
        return code != null && code.equalsIgnoreCase(errorCode);
    }
    
    /**
     * Get the error code from payload if present.
     */
    public String getErrorCode() {
        return payload != null ? payload.get("errorCode") : null;
    }
}
