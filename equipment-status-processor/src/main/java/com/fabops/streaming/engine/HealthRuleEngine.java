package com.fabops.streaming.engine;

import com.fabops.streaming.config.HealthRuleProperties;
import com.fabops.streaming.model.EquipmentHealth;
import com.fabops.streaming.model.EquipmentHealth.HealthStatus;
import com.fabops.streaming.model.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rule engine that evaluates telemetry data to produce equipment health assessments.
 * 
 * Evaluates:
 * - Temperature against warning/critical/max thresholds
 * - Pressure against min/warning/critical thresholds
 * - Error codes against known critical/warning codes
 * 
 * Produces a weighted health score (0-100) and critical alert flag.
 */
@Component
public class HealthRuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(HealthRuleEngine.class);

    private static final int HEALTH_SCORE_MAX = 100;
    private static final int HEALTH_SCORE_CRITICAL_THRESHOLD = 40;
    private static final int HEALTH_SCORE_WARNING_THRESHOLD = 70;

    private final HealthRuleProperties properties;

    public HealthRuleEngine(HealthRuleProperties properties) {
        this.properties = properties;
    }

    /**
     * Evaluate telemetry data and produce an EquipmentHealth assessment.
     * 
     * @param telemetry The raw telemetry data to evaluate
     * @return EquipmentHealth with healthScore and isCritical flag
     */
    public EquipmentHealth evaluate(Telemetry telemetry) {
        Map<String, String> payload = telemetry.getPayload();
        if (payload == null) {
            payload = Map.of();
        }

        // Extract metrics from payload
        Double temperature = extractDouble(payload, "temperature");
        Double pressure = extractDouble(payload, "pressure");
        String errorCode = payload.get("errorCode");

        // Calculate individual scores
        int temperatureScore = evaluateTemperature(temperature);
        int pressureScore = evaluatePressure(pressure);
        int errorCodeScore = evaluateErrorCode(errorCode);

        // Calculate weighted health score
        var weights = properties.getWeights();
        int healthScore = (int) Math.round(
            temperatureScore * weights.getTemperature() +
            pressureScore * weights.getPressure() +
            errorCodeScore * weights.getErrorCode()
        );

        // Determine criticality and status
        boolean isCritical = healthScore < HEALTH_SCORE_CRITICAL_THRESHOLD ||
                            isCriticalErrorCode(errorCode) ||
                            isCriticalTemperature(temperature) ||
                            isCriticalPressure(pressure);

        HealthStatus status = determineStatus(healthScore, isCritical);

        // Build equipment health result
        EquipmentHealth health = new EquipmentHealth();
        health.setEquipmentId(telemetry.getEquipmentId());
        health.setTimestamp(System.currentTimeMillis());
        health.setHealthScore(healthScore);
        health.setCritical(isCritical);
        health.setStatus(status);
        health.setCorrelationId(UUID.randomUUID().toString());
        health.setSourceEventCorrelationId(telemetry.getCorrelationId());
        health.setDetails(buildDetails(temperature, pressure, errorCode, 
                                       temperatureScore, pressureScore, errorCodeScore));

        // Generate alert info if critical
        if (isCritical) {
            health.setAlertTitle(generateAlertTitle(telemetry.getEquipmentId(), temperature, pressure, errorCode));
            health.setAlertDescription(generateAlertDescription(telemetry.getEquipmentId(), 
                                                                 temperature, pressure, errorCode, healthScore));
        }

        logger.debug("Evaluated telemetry for equipment {}: score={}, critical={}", 
                     telemetry.getEquipmentId(), healthScore, isCritical);

        return health;
    }

    /**
     * Evaluate temperature against configured thresholds.
     * Returns score 0-100 where 100 is optimal.
     */
    public static int evaluateTemperature(Double temperature, HealthRuleProperties.TemperatureThresholds thresholds) {
        if (temperature == null) {
            return HEALTH_SCORE_MAX; // No data, assume healthy
        }

        if (temperature >= thresholds.getMaxThreshold()) {
            return 0; // Maximum exceeded - critical
        } else if (temperature >= thresholds.getCriticalThreshold()) {
            // Linear scale from critical to max: 0-25
            double range = thresholds.getMaxThreshold() - thresholds.getCriticalThreshold();
            double position = temperature - thresholds.getCriticalThreshold();
            return (int) Math.round(25 * (1 - position / range));
        } else if (temperature >= thresholds.getWarningThreshold()) {
            // Linear scale from warning to critical: 25-70
            double range = thresholds.getCriticalThreshold() - thresholds.getWarningThreshold();
            double position = temperature - thresholds.getWarningThreshold();
            return (int) Math.round(70 - 45 * (position / range));
        } else {
            return HEALTH_SCORE_MAX; // Below warning threshold - healthy
        }
    }

    private int evaluateTemperature(Double temperature) {
        return evaluateTemperature(temperature, properties.getTemperature());
    }

    /**
     * Evaluate pressure against configured thresholds.
     * Returns score 0-100 where 100 is optimal (pressure within normal range).
     */
    public static int evaluatePressure(Double pressure, HealthRuleProperties.PressureThresholds thresholds) {
        if (pressure == null) {
            return HEALTH_SCORE_MAX; // No data, assume healthy
        }

        // Check critical conditions
        if (pressure < thresholds.getMinThreshold() || pressure >= thresholds.getCriticalThreshold()) {
            return 0; // Critical - outside safe bounds
        }

        // Check low pressure warning
        if (pressure < thresholds.getWarningLowThreshold()) {
            double range = thresholds.getWarningLowThreshold() - thresholds.getMinThreshold();
            double position = pressure - thresholds.getMinThreshold();
            return (int) Math.round(70 * (position / range));
        }

        // Check high pressure warning
        if (pressure >= thresholds.getWarningHighThreshold()) {
            double range = thresholds.getCriticalThreshold() - thresholds.getWarningHighThreshold();
            double position = pressure - thresholds.getWarningHighThreshold();
            return (int) Math.round(70 * (1 - position / range));
        }

        return HEALTH_SCORE_MAX; // Within normal range - healthy
    }

    private int evaluatePressure(Double pressure) {
        return evaluatePressure(pressure, properties.getPressure());
    }

    /**
     * Evaluate error code against configured critical/warning codes.
     * Returns score 0-100 based on error severity.
     */
    public static int evaluateErrorCode(String errorCode, HealthRuleProperties.ErrorCodeConfig config) {
        if (errorCode == null || errorCode.isBlank()) {
            return HEALTH_SCORE_MAX; // No error - healthy
        }

        if (config.getCriticalCodes().contains(errorCode)) {
            return 0; // Critical error
        }

        if (config.getWarningCodes().contains(errorCode)) {
            return 50; // Warning error
        }

        return 80; // Unknown error code - minor concern
    }

    private int evaluateErrorCode(String errorCode) {
        return evaluateErrorCode(errorCode, properties.getErrorCodes());
    }

    private boolean isCriticalErrorCode(String errorCode) {
        return errorCode != null && properties.getErrorCodes().getCriticalCodes().contains(errorCode);
    }

    private boolean isCriticalTemperature(Double temperature) {
        return temperature != null && temperature >= properties.getTemperature().getCriticalThreshold();
    }

    private boolean isCriticalPressure(Double pressure) {
        if (pressure == null) return false;
        return pressure < properties.getPressure().getMinThreshold() || 
               pressure >= properties.getPressure().getCriticalThreshold();
    }

    private HealthStatus determineStatus(int healthScore, boolean isCritical) {
        if (isCritical || healthScore < HEALTH_SCORE_CRITICAL_THRESHOLD) {
            return HealthStatus.CRITICAL;
        } else if (healthScore < HEALTH_SCORE_WARNING_THRESHOLD) {
            return HealthStatus.WARNING;
        } else {
            return HealthStatus.HEALTHY;
        }
    }

    private Map<String, String> buildDetails(Double temperature, Double pressure, String errorCode,
                                              int tempScore, int pressureScore, int errorScore) {
        Map<String, String> details = new HashMap<>();
        
        if (temperature != null) {
            details.put("temperature", String.valueOf(temperature));
            details.put("temperatureScore", String.valueOf(tempScore));
        }
        if (pressure != null) {
            details.put("pressure", String.valueOf(pressure));
            details.put("pressureScore", String.valueOf(pressureScore));
        }
        if (errorCode != null) {
            details.put("errorCode", errorCode);
            details.put("errorCodeScore", String.valueOf(errorScore));
        }
        
        return details;
    }

    private String generateAlertTitle(String equipmentId, Double temperature, Double pressure, String errorCode) {
        if (isCriticalErrorCode(errorCode)) {
            return String.format("Critical Error %s on Equipment %s", errorCode, equipmentId);
        }
        if (isCriticalTemperature(temperature)) {
            return String.format("Critical Temperature Alert on Equipment %s", equipmentId);
        }
        if (isCriticalPressure(pressure)) {
            return String.format("Critical Pressure Alert on Equipment %s", equipmentId);
        }
        return String.format("Health Alert on Equipment %s", equipmentId);
    }

    private String generateAlertDescription(String equipmentId, Double temperature, Double pressure, 
                                            String errorCode, int healthScore) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Equipment %s health score: %d/100. ", equipmentId, healthScore));

        if (isCriticalErrorCode(errorCode)) {
            sb.append(String.format("Critical error code %s detected. ", errorCode));
        }
        if (isCriticalTemperature(temperature)) {
            sb.append(String.format("Temperature %.1f°C exceeds critical threshold %.1f°C. ", 
                      temperature, properties.getTemperature().getCriticalThreshold()));
        }
        if (isCriticalPressure(pressure)) {
            if (pressure < properties.getPressure().getMinThreshold()) {
                sb.append(String.format("Pressure %.2f below minimum threshold %.2f. ", 
                          pressure, properties.getPressure().getMinThreshold()));
            } else {
                sb.append(String.format("Pressure %.2f exceeds critical threshold %.2f. ", 
                          pressure, properties.getPressure().getCriticalThreshold()));
            }
        }

        sb.append("Immediate attention required.");
        return sb.toString();
    }

    private Double extractDouble(Map<String, String> payload, String key) {
        String value = payload.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse {} value: {}", key, value);
            return null;
        }
    }
}
