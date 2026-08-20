package com.fabops.streaming.equipmentstatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

/**
 * Critical alert event for equipment health violations.
 * Maps to CriticalAlertEvent Avro schema.
 */
public record CriticalAlert(
    @JsonProperty("equipmentId") String equipmentId,
    @JsonProperty("timestamp") long timestamp,
    @JsonProperty("alertId") String alertId,
    @JsonProperty("severity") AlertSeverity severity,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("threshold") String threshold,
    @JsonProperty("observedValue") String observedValue,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("schemaVersion") String schemaVersion,
    @JsonProperty("acknowledged") boolean acknowledged,
    @JsonProperty("attributes") Map<String, String> attributes
) {
    
    /**
     * Create a critical alert from equipment health assessment.
     */
    public static CriticalAlert fromHealthViolation(
            String equipmentId,
            long timestamp,
            HealthViolation violation,
            String correlationId,
            int healthScore) {
        
        AlertSeverity severity = violation.severity() == HealthViolation.Severity.CRITICAL 
            ? AlertSeverity.CRITICAL 
            : AlertSeverity.HIGH;
        
        return new CriticalAlert(
            equipmentId,
            timestamp,
            UUID.randomUUID().toString(),
            severity,
            String.format("Equipment %s: %s Alert", equipmentId, violation.rule()),
            violation.message(),
            violation.threshold(),
            violation.observedValue(),
            correlationId,
            "1.0.0",
            false,
            Map.of(
                "healthScore", String.valueOf(healthScore),
                "metric", violation.metric(),
                "rule", violation.rule()
            )
        );
    }
    
    /**
     * Create a critical alert for overall critical health status.
     */
    public static CriticalAlert fromCriticalHealth(EquipmentHealth health) {
        String description = health.getViolations().isEmpty()
            ? String.format("Equipment %s health score dropped to critical level: %d", 
                health.getEquipmentId(), health.getHealthScore())
            : String.format("Equipment %s has %d health violations with health score: %d",
                health.getEquipmentId(), health.getViolations().size(), health.getHealthScore());
        
        return new CriticalAlert(
            health.getEquipmentId(),
            health.getTimestamp(),
            UUID.randomUUID().toString(),
            AlertSeverity.CRITICAL,
            String.format("Critical Health Alert: %s", health.getEquipmentId()),
            description,
            "50",
            String.valueOf(health.getHealthScore()),
            health.getCorrelationId(),
            "1.0.0",
            false,
            Map.of(
                "healthScore", String.valueOf(health.getHealthScore()),
                "status", health.getStatus().name(),
                "violationCount", String.valueOf(health.getViolations().size())
            )
        );
    }
}
