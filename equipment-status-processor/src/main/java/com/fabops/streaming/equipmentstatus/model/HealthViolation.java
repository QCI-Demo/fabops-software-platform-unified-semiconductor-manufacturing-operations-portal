package com.fabops.streaming.equipmentstatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a health rule violation detected during equipment health assessment.
 */
public record HealthViolation(
    @JsonProperty("rule") String rule,
    @JsonProperty("severity") Severity severity,
    @JsonProperty("metric") String metric,
    @JsonProperty("threshold") String threshold,
    @JsonProperty("observedValue") String observedValue,
    @JsonProperty("message") String message
) {
    
    public enum Severity {
        WARNING,
        CRITICAL
    }
    
    public static HealthViolation warning(String rule, String metric, String threshold, String observedValue) {
        return new HealthViolation(
            rule,
            Severity.WARNING,
            metric,
            threshold,
            observedValue,
            String.format("%s warning: %s exceeded threshold %s (observed: %s)", rule, metric, threshold, observedValue)
        );
    }
    
    public static HealthViolation critical(String rule, String metric, String threshold, String observedValue) {
        return new HealthViolation(
            rule,
            Severity.CRITICAL,
            metric,
            threshold,
            observedValue,
            String.format("%s critical: %s exceeded threshold %s (observed: %s)", rule, metric, threshold, observedValue)
        );
    }
}
