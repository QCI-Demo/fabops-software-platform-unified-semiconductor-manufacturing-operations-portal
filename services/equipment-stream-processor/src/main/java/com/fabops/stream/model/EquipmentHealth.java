package com.fabops.stream.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Equipment health assessment result.
 * Versioned record supporting schema evolution.
 */
public record EquipmentHealth(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("equipmentId") String equipmentId,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("healthScore") int healthScore,
    @JsonProperty("isCritical") boolean isCritical,
    @JsonProperty("severity") HealthSeverity severity,
    @JsonProperty("temperatureStatus") String temperatureStatus,
    @JsonProperty("pressureStatus") String pressureStatus,
    @JsonProperty("errorStatus") String errorStatus,
    @JsonProperty("sourceEventId") String sourceEventId,
    @JsonProperty("schemaVersion") int schemaVersion
) {
    
    public enum HealthSeverity {
        HEALTHY,
        WARNING,
        CRITICAL
    }

    /**
     * Builder for EquipmentHealth with sensible defaults.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private String equipmentId;
        private String correlationId;
        private Instant timestamp = Instant.now();
        private int healthScore = 100;
        private boolean isCritical = false;
        private HealthSeverity severity = HealthSeverity.HEALTHY;
        private String temperatureStatus = "NORMAL";
        private String pressureStatus = "NORMAL";
        private String errorStatus = "NONE";
        private String sourceEventId;
        private int schemaVersion = 1;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder equipmentId(String equipmentId) {
            this.equipmentId = equipmentId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
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

        public Builder temperatureStatus(String temperatureStatus) {
            this.temperatureStatus = temperatureStatus;
            return this;
        }

        public Builder pressureStatus(String pressureStatus) {
            this.pressureStatus = pressureStatus;
            return this;
        }

        public Builder errorStatus(String errorStatus) {
            this.errorStatus = errorStatus;
            return this;
        }

        public Builder sourceEventId(String sourceEventId) {
            this.sourceEventId = sourceEventId;
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public EquipmentHealth build() {
            return new EquipmentHealth(
                eventId, equipmentId, correlationId, timestamp,
                healthScore, isCritical, severity,
                temperatureStatus, pressureStatus, errorStatus,
                sourceEventId, schemaVersion
            );
        }
    }
}
