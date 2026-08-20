package com.fabops.stream.engine;

import com.fabops.stream.config.HealthThresholdsConfig;
import com.fabops.stream.model.CriticalAlert;
import com.fabops.stream.model.EquipmentHealth;
import com.fabops.stream.model.EquipmentHealth.HealthSeverity;
import com.fabops.stream.model.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Health Rule Engine evaluates telemetry data to produce health scores and detect critical conditions.
 * 
 * This engine:
 * - Evaluates temperature against configured thresholds
 * - Evaluates pressure against configured thresholds
 * - Checks error codes against known critical/warning patterns
 * - Computes weighted health score
 * - Determines overall severity and critical flag
 */
@Component
public class HealthRuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(HealthRuleEngine.class);
    
    private final HealthThresholdsConfig config;

    public HealthRuleEngine(HealthThresholdsConfig config) {
        this.config = config;
    }

    /**
     * Evaluates telemetry and produces an EquipmentHealth assessment.
     * 
     * @param telemetry the incoming telemetry event
     * @return equipment health assessment with score and severity
     */
    public EquipmentHealth evaluate(Telemetry telemetry) {
        logger.debug("Evaluating telemetry for equipment: {}", telemetry.equipmentId());
        
        // Evaluate each dimension
        TemperatureResult tempResult = evaluateTemperature(telemetry.temperature());
        PressureResult pressureResult = evaluatePressure(telemetry.pressure());
        ErrorResult errorResult = evaluateErrorCodes(telemetry.errorCodes());
        
        // Calculate weighted health score
        int healthScore = calculateHealthScore(tempResult, pressureResult, errorResult);
        
        // Determine overall severity
        HealthSeverity severity = determineSeverity(tempResult, pressureResult, errorResult, healthScore);
        boolean isCritical = severity == HealthSeverity.CRITICAL;
        
        logger.info("Equipment {} health score: {}, severity: {}, critical: {}",
                telemetry.equipmentId(), healthScore, severity, isCritical);
        
        return EquipmentHealth.builder()
                .eventId(UUID.randomUUID().toString())
                .equipmentId(telemetry.equipmentId())
                .correlationId(telemetry.correlationId())
                .timestamp(Instant.now())
                .healthScore(healthScore)
                .isCritical(isCritical)
                .severity(severity)
                .temperatureStatus(tempResult.status())
                .pressureStatus(pressureResult.status())
                .errorStatus(errorResult.status())
                .sourceEventId(telemetry.eventId())
                .schemaVersion(1)
                .build();
    }

    /**
     * Evaluates temperature against thresholds.
     */
    public static TemperatureResult evaluateTemperature(Double temperature) {
        return evaluateTemperature(temperature, 75.0, 90.0);
    }

    /**
     * Evaluates temperature against configurable thresholds.
     */
    public TemperatureResult evaluateTemperatureWithConfig(Double temperature) {
        var thresholds = config.getThresholds().getTemperature();
        return evaluateTemperature(temperature, thresholds.getWarning(), thresholds.getCritical());
    }

    private static TemperatureResult evaluateTemperature(Double temperature, double warningThreshold, double criticalThreshold) {
        if (temperature == null) {
            return new TemperatureResult("UNKNOWN", 100, false, false);
        }
        
        if (temperature >= criticalThreshold) {
            return new TemperatureResult("CRITICAL", 0, false, true);
        } else if (temperature >= warningThreshold) {
            // Linear degradation between warning and critical
            double ratio = (temperature - warningThreshold) / (criticalThreshold - warningThreshold);
            int score = (int) (100 - (ratio * 100));
            return new TemperatureResult("WARNING", Math.max(0, score), true, false);
        }
        return new TemperatureResult("NORMAL", 100, false, false);
    }

    /**
     * Evaluates pressure against thresholds.
     */
    public static PressureResult evaluatePressure(Double pressure) {
        return evaluatePressure(pressure, 0.8, 1.2, 0.5, 1.5);
    }

    /**
     * Evaluates pressure against configurable thresholds.
     */
    public PressureResult evaluatePressureWithConfig(Double pressure) {
        var thresholds = config.getThresholds().getPressure();
        return evaluatePressure(pressure, 
                thresholds.getWarningLow(), thresholds.getWarningHigh(),
                thresholds.getCriticalLow(), thresholds.getCriticalHigh());
    }

    private static PressureResult evaluatePressure(Double pressure, 
            double warningLow, double warningHigh, double criticalLow, double criticalHigh) {
        if (pressure == null) {
            return new PressureResult("UNKNOWN", 100, false, false);
        }
        
        if (pressure <= criticalLow || pressure >= criticalHigh) {
            return new PressureResult("CRITICAL", 0, false, true);
        } else if (pressure <= warningLow || pressure >= warningHigh) {
            // Calculate how far into warning zone
            double ratio;
            if (pressure <= warningLow) {
                ratio = (warningLow - pressure) / (warningLow - criticalLow);
            } else {
                ratio = (pressure - warningHigh) / (criticalHigh - warningHigh);
            }
            int score = (int) (100 - (ratio * 100));
            return new PressureResult("WARNING", Math.max(0, score), true, false);
        }
        return new PressureResult("NORMAL", 100, false, false);
    }

    /**
     * Evaluates error codes against known critical/warning patterns.
     */
    public static ErrorResult evaluateErrorCodes(List<String> errorCodes) {
        return evaluateErrorCodes(errorCodes, 
                List.of("E001", "E002", "E003", "E010", "E020"),
                List.of("W001", "W002", "W003", "W010"));
    }

    /**
     * Evaluates error codes against configurable patterns.
     */
    public ErrorResult evaluateErrorCodesWithConfig(List<String> errorCodes) {
        var errorConfig = config.getThresholds().getErrorCodes();
        return evaluateErrorCodes(errorCodes, errorConfig.getCritical(), errorConfig.getWarning());
    }

    private static ErrorResult evaluateErrorCodes(List<String> errorCodes, 
            List<String> criticalCodes, List<String> warningCodes) {
        if (errorCodes == null || errorCodes.isEmpty()) {
            return new ErrorResult("NONE", 100, false, false, List.of());
        }
        
        List<String> matchedCritical = new ArrayList<>();
        List<String> matchedWarning = new ArrayList<>();
        
        for (String code : errorCodes) {
            if (criticalCodes.contains(code)) {
                matchedCritical.add(code);
            } else if (warningCodes.contains(code)) {
                matchedWarning.add(code);
            }
        }
        
        if (!matchedCritical.isEmpty()) {
            return new ErrorResult("CRITICAL", 0, false, true, matchedCritical);
        } else if (!matchedWarning.isEmpty()) {
            // Score degrades based on number of warnings
            int score = Math.max(0, 100 - (matchedWarning.size() * 25));
            return new ErrorResult("WARNING", score, true, false, matchedWarning);
        }
        
        return new ErrorResult("NONE", 100, false, false, List.of());
    }

    /**
     * Calculates weighted health score from dimension results.
     */
    public int calculateHealthScore(TemperatureResult temp, PressureResult pressure, ErrorResult error) {
        var scoring = config.getScoring();
        
        int tempWeighted = (temp.score() * scoring.getTemperatureWeight()) / 100;
        int pressureWeighted = (pressure.score() * scoring.getPressureWeight()) / 100;
        int errorWeighted = (error.score() * scoring.getErrorWeight()) / 100;
        
        return Math.max(0, Math.min(100, tempWeighted + pressureWeighted + errorWeighted));
    }

    /**
     * Calculates health score with default weights (30/30/40).
     */
    public static int calculateHealthScoreStatic(TemperatureResult temp, PressureResult pressure, ErrorResult error) {
        int tempWeighted = (temp.score() * 30) / 100;
        int pressureWeighted = (pressure.score() * 30) / 100;
        int errorWeighted = (error.score() * 40) / 100;
        
        return Math.max(0, Math.min(100, tempWeighted + pressureWeighted + errorWeighted));
    }

    /**
     * Determines overall severity based on dimension results and score.
     */
    public static HealthSeverity determineSeverity(TemperatureResult temp, PressureResult pressure, 
            ErrorResult error, int healthScore) {
        // Any critical dimension makes overall critical
        if (temp.isCritical() || pressure.isCritical() || error.isCritical()) {
            return HealthSeverity.CRITICAL;
        }
        
        // Multiple warnings or low health score is critical
        int warningCount = (temp.isWarning() ? 1 : 0) + (pressure.isWarning() ? 1 : 0) + (error.isWarning() ? 1 : 0);
        if (warningCount >= 2 || healthScore < 40) {
            return HealthSeverity.CRITICAL;
        }
        
        // Any warning dimension
        if (temp.isWarning() || pressure.isWarning() || error.isWarning()) {
            return HealthSeverity.WARNING;
        }
        
        return HealthSeverity.HEALTHY;
    }

    /**
     * Creates a critical alert from health assessment and telemetry.
     */
    public CriticalAlert createCriticalAlert(EquipmentHealth health, Telemetry telemetry) {
        CriticalAlert.AlertType alertType = determineAlertType(health);
        String message = buildAlertMessage(health, telemetry);
        
        return CriticalAlert.fromHealthAndTelemetry(health, telemetry, alertType, message);
    }

    private CriticalAlert.AlertType determineAlertType(EquipmentHealth health) {
        boolean tempCritical = "CRITICAL".equals(health.temperatureStatus());
        boolean pressureCritical = "CRITICAL".equals(health.pressureStatus());
        boolean errorCritical = "CRITICAL".equals(health.errorStatus());
        
        int criticalCount = (tempCritical ? 1 : 0) + (pressureCritical ? 1 : 0) + (errorCritical ? 1 : 0);
        
        if (criticalCount > 1) {
            return CriticalAlert.AlertType.COMBINED_CRITICAL;
        } else if (tempCritical) {
            return CriticalAlert.AlertType.TEMPERATURE_CRITICAL;
        } else if (pressureCritical) {
            return CriticalAlert.AlertType.PRESSURE_CRITICAL;
        } else if (errorCritical) {
            return CriticalAlert.AlertType.ERROR_CODE_CRITICAL;
        }
        return CriticalAlert.AlertType.COMBINED_CRITICAL;
    }

    private String buildAlertMessage(EquipmentHealth health, Telemetry telemetry) {
        StringBuilder sb = new StringBuilder();
        sb.append("Equipment ").append(health.equipmentId())
          .append(" in CRITICAL state. Health score: ").append(health.healthScore());
        
        if ("CRITICAL".equals(health.temperatureStatus()) && telemetry.temperature() != null) {
            sb.append(". Temperature: ").append(String.format("%.1f", telemetry.temperature())).append("°C");
        }
        if ("CRITICAL".equals(health.pressureStatus()) && telemetry.pressure() != null) {
            sb.append(". Pressure: ").append(String.format("%.2f", telemetry.pressure())).append(" bar");
        }
        if ("CRITICAL".equals(health.errorStatus()) && telemetry.errorCodes() != null) {
            sb.append(". Error codes: ").append(telemetry.errorCodes());
        }
        
        return sb.toString();
    }

    // Result records for dimension evaluations
    public record TemperatureResult(String status, int score, boolean isWarning, boolean isCritical) {}
    public record PressureResult(String status, int score, boolean isWarning, boolean isCritical) {}
    public record ErrorResult(String status, int score, boolean isWarning, boolean isCritical, List<String> matchedCodes) {}
}
