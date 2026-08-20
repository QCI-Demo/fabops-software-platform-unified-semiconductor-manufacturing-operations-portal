package com.fabops.streaming.equipmentstatus.engine;

import com.fabops.streaming.equipmentstatus.config.HealthThresholdsConfig;
import com.fabops.streaming.equipmentstatus.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Health Rule Engine that evaluates telemetry data against configurable thresholds
 * to produce equipment health scores and detect critical conditions.
 * 
 * Evaluates:
 * - Temperature thresholds (warning/critical)
 * - Pressure range thresholds (low/high warning/critical)
 * - Error codes (critical code list)
 * - Vibration thresholds (warning/critical)
 * - Humidity range thresholds (low/high warning/critical)
 * 
 * Thread-safe: uses only immutable config and local state.
 */
@Component
public class HealthRuleEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthRuleEngine.class);
    
    private final HealthThresholdsConfig config;
    
    public HealthRuleEngine(HealthThresholdsConfig config) {
        this.config = config;
    }
    
    /**
     * Evaluate telemetry and compute equipment health.
     * 
     * @param telemetry Raw telemetry event
     * @return EquipmentHealth with score, critical flag, and violations
     */
    public EquipmentHealth evaluate(Telemetry telemetry) {
        logger.debug("Evaluating health for equipment: {}", telemetry.equipmentId());
        
        List<HealthViolation> violations = new ArrayList<>();
        int score = config.getScoring().getBaseScore();
        
        // Evaluate temperature
        score = evaluateTemperature(telemetry, violations, score);
        
        // Evaluate pressure
        score = evaluatePressure(telemetry, violations, score);
        
        // Evaluate error codes
        score = evaluateErrorCodes(telemetry, violations, score);
        
        // Evaluate vibration
        score = evaluateVibration(telemetry, violations, score);
        
        // Evaluate humidity
        score = evaluateHumidity(telemetry, violations, score);
        
        // Ensure score stays in valid range
        score = Math.max(0, Math.min(100, score));
        
        // Determine if critical
        boolean isCritical = score < config.getScoring().getCriticalThreshold() ||
                violations.stream().anyMatch(v -> v.severity() == HealthViolation.Severity.CRITICAL);
        
        // Determine equipment status based on health
        EquipmentStatus status = determineStatus(score, violations);
        
        EquipmentHealth health = EquipmentHealth.builder()
                .equipmentId(telemetry.equipmentId())
                .timestamp(telemetry.timestamp())
                .healthScore(score)
                .isCritical(isCritical)
                .status(status)
                .violations(violations)
                .correlationId(telemetry.correlationId())
                .schemaVersion("1.0.0")
                .build();
        
        logger.debug("Health evaluation complete: {}", health);
        return health;
    }
    
    /**
     * Static evaluation method for use in stream processing without Spring context.
     */
    public static EquipmentHealth evaluateWithDefaults(Telemetry telemetry) {
        HealthThresholdsConfig defaultConfig = new HealthThresholdsConfig();
        HealthRuleEngine engine = new HealthRuleEngine(defaultConfig);
        return engine.evaluate(telemetry);
    }
    
    private int evaluateTemperature(Telemetry telemetry, List<HealthViolation> violations, int score) {
        Double temp = telemetry.getNumericValue("temperature");
        if (temp == null) {
            return score;
        }
        
        HealthThresholdsConfig.Temperature thresholds = config.getThresholds().getTemperature();
        
        if (temp >= thresholds.getCritical()) {
            violations.add(HealthViolation.critical(
                    "TEMPERATURE_CRITICAL",
                    "temperature",
                    String.valueOf(thresholds.getCritical()),
                    String.valueOf(temp)
            ));
            score -= config.getScoring().getTemperatureCriticalPenalty();
            logger.warn("Critical temperature detected: {} >= {}", temp, thresholds.getCritical());
        } else if (temp >= thresholds.getWarning()) {
            violations.add(HealthViolation.warning(
                    "TEMPERATURE_WARNING",
                    "temperature",
                    String.valueOf(thresholds.getWarning()),
                    String.valueOf(temp)
            ));
            score -= config.getScoring().getTemperatureWarningPenalty();
            logger.info("Temperature warning: {} >= {}", temp, thresholds.getWarning());
        }
        
        return score;
    }
    
    private int evaluatePressure(Telemetry telemetry, List<HealthViolation> violations, int score) {
        Double pressure = telemetry.getNumericValue("pressure");
        if (pressure == null) {
            return score;
        }
        
        HealthThresholdsConfig.Pressure thresholds = config.getThresholds().getPressure();
        
        // Check critical low
        if (pressure <= thresholds.getCriticalLow()) {
            violations.add(HealthViolation.critical(
                    "PRESSURE_CRITICAL_LOW",
                    "pressure",
                    String.valueOf(thresholds.getCriticalLow()),
                    String.valueOf(pressure)
            ));
            score -= config.getScoring().getPressureCriticalPenalty();
            logger.warn("Critical low pressure: {} <= {}", pressure, thresholds.getCriticalLow());
        }
        // Check critical high
        else if (pressure >= thresholds.getCriticalHigh()) {
            violations.add(HealthViolation.critical(
                    "PRESSURE_CRITICAL_HIGH",
                    "pressure",
                    String.valueOf(thresholds.getCriticalHigh()),
                    String.valueOf(pressure)
            ));
            score -= config.getScoring().getPressureCriticalPenalty();
            logger.warn("Critical high pressure: {} >= {}", pressure, thresholds.getCriticalHigh());
        }
        // Check warning low
        else if (pressure <= thresholds.getWarningLow()) {
            violations.add(HealthViolation.warning(
                    "PRESSURE_WARNING_LOW",
                    "pressure",
                    String.valueOf(thresholds.getWarningLow()),
                    String.valueOf(pressure)
            ));
            score -= config.getScoring().getPressureWarningPenalty();
            logger.info("Low pressure warning: {} <= {}", pressure, thresholds.getWarningLow());
        }
        // Check warning high
        else if (pressure >= thresholds.getWarningHigh()) {
            violations.add(HealthViolation.warning(
                    "PRESSURE_WARNING_HIGH",
                    "pressure",
                    String.valueOf(thresholds.getWarningHigh()),
                    String.valueOf(pressure)
            ));
            score -= config.getScoring().getPressureWarningPenalty();
            logger.info("High pressure warning: {} >= {}", pressure, thresholds.getWarningHigh());
        }
        
        return score;
    }
    
    private int evaluateErrorCodes(Telemetry telemetry, List<HealthViolation> violations, int score) {
        String errorCode = telemetry.getErrorCode();
        if (errorCode == null || errorCode.isEmpty()) {
            return score;
        }
        
        List<String> criticalCodes = config.getThresholds().getErrorCode().getCriticalCodes();
        
        if (criticalCodes.stream().anyMatch(code -> code.equalsIgnoreCase(errorCode))) {
            violations.add(HealthViolation.critical(
                    "ERROR_CODE_CRITICAL",
                    "errorCode",
                    String.join(",", criticalCodes),
                    errorCode
            ));
            score -= config.getScoring().getErrorCodeCriticalPenalty();
            logger.warn("Critical error code detected: {}", errorCode);
        }
        
        return score;
    }
    
    private int evaluateVibration(Telemetry telemetry, List<HealthViolation> violations, int score) {
        Double vibration = telemetry.getNumericValue("vibration");
        if (vibration == null) {
            return score;
        }
        
        HealthThresholdsConfig.Vibration thresholds = config.getThresholds().getVibration();
        
        if (vibration >= thresholds.getCritical()) {
            violations.add(HealthViolation.critical(
                    "VIBRATION_CRITICAL",
                    "vibration",
                    String.valueOf(thresholds.getCritical()),
                    String.valueOf(vibration)
            ));
            score -= config.getScoring().getVibrationCriticalPenalty();
            logger.warn("Critical vibration: {} >= {}", vibration, thresholds.getCritical());
        } else if (vibration >= thresholds.getWarning()) {
            violations.add(HealthViolation.warning(
                    "VIBRATION_WARNING",
                    "vibration",
                    String.valueOf(thresholds.getWarning()),
                    String.valueOf(vibration)
            ));
            score -= config.getScoring().getVibrationWarningPenalty();
            logger.info("Vibration warning: {} >= {}", vibration, thresholds.getWarning());
        }
        
        return score;
    }
    
    private int evaluateHumidity(Telemetry telemetry, List<HealthViolation> violations, int score) {
        Double humidity = telemetry.getNumericValue("humidity");
        if (humidity == null) {
            return score;
        }
        
        HealthThresholdsConfig.Humidity thresholds = config.getThresholds().getHumidity();
        
        // Check critical ranges
        if (humidity <= thresholds.getCriticalLow()) {
            violations.add(HealthViolation.critical(
                    "HUMIDITY_CRITICAL_LOW",
                    "humidity",
                    String.valueOf(thresholds.getCriticalLow()),
                    String.valueOf(humidity)
            ));
            score -= config.getScoring().getHumidityCriticalPenalty();
        } else if (humidity >= thresholds.getCriticalHigh()) {
            violations.add(HealthViolation.critical(
                    "HUMIDITY_CRITICAL_HIGH",
                    "humidity",
                    String.valueOf(thresholds.getCriticalHigh()),
                    String.valueOf(humidity)
            ));
            score -= config.getScoring().getHumidityCriticalPenalty();
        }
        // Check warning ranges
        else if (humidity <= thresholds.getWarningLow()) {
            violations.add(HealthViolation.warning(
                    "HUMIDITY_WARNING_LOW",
                    "humidity",
                    String.valueOf(thresholds.getWarningLow()),
                    String.valueOf(humidity)
            ));
            score -= config.getScoring().getHumidityWarningPenalty();
        } else if (humidity >= thresholds.getWarningHigh()) {
            violations.add(HealthViolation.warning(
                    "HUMIDITY_WARNING_HIGH",
                    "humidity",
                    String.valueOf(thresholds.getWarningHigh()),
                    String.valueOf(humidity)
            ));
            score -= config.getScoring().getHumidityWarningPenalty();
        }
        
        return score;
    }
    
    private EquipmentStatus determineStatus(int score, List<HealthViolation> violations) {
        boolean hasCriticalViolation = violations.stream()
                .anyMatch(v -> v.severity() == HealthViolation.Severity.CRITICAL);
        
        if (hasCriticalViolation || score < 30) {
            return EquipmentStatus.DOWN;
        } else if (score < 50) {
            return EquipmentStatus.MAINTENANCE;
        } else if (score < 80) {
            return EquipmentStatus.RUNNING; // Running but with warnings
        } else {
            return EquipmentStatus.RUNNING;
        }
    }
}
