package com.fabops.equipment.processor.engine;

import com.fabops.equipment.processor.config.HealthThresholdProperties;
import com.fabops.equipment.processor.model.EquipmentHealth;
import com.fabops.equipment.processor.model.EquipmentHealth.HealthBreakdown;
import com.fabops.equipment.processor.model.EquipmentHealth.HealthSeverity;
import com.fabops.equipment.processor.model.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Health Rule Engine that evaluates telemetry data and produces health scores.
 * 
 * Evaluates:
 * - Temperature thresholds (warning/critical)
 * - Pressure ranges (low/high warning/critical)
 * - Error codes (warning/critical classifications)
 * 
 * Returns an EquipmentHealth POJO with:
 * - healthScore: 0-100 weighted composite score
 * - isCritical: boolean flag for critical alert branching
 * - severity: HEALTHY, WARNING, or CRITICAL
 * - breakdown: detailed scoring per category
 */
@Component
public class HealthRuleEngine {

	private static final Logger log = LoggerFactory.getLogger(HealthRuleEngine.class);

	private final HealthThresholdProperties config;

	public HealthRuleEngine(HealthThresholdProperties config) {
		this.config = config;
	}

	/**
	 * Evaluates telemetry data and produces an EquipmentHealth result.
	 *
	 * @param telemetry the raw telemetry data to evaluate
	 * @return EquipmentHealth with computed score and severity
	 */
	public EquipmentHealth evaluate(Telemetry telemetry) {
		List<String> violations = new ArrayList<>();

		// Calculate individual component scores
		int temperatureScore = evaluateTemperature(telemetry.temperature(), violations);
		int pressureScore = evaluatePressure(telemetry.pressure(), violations);
		int errorCodeScore = evaluateErrorCodes(telemetry.errorCodes(), violations);

		// Compute weighted health score
		var scoring = config.getScoring();
		int totalWeight = scoring.getTemperatureWeight() + scoring.getPressureWeight() + scoring.getErrorCodeWeight();
		
		int weightedScore = (
				(temperatureScore * scoring.getTemperatureWeight()) +
				(pressureScore * scoring.getPressureWeight()) +
				(errorCodeScore * scoring.getErrorCodeWeight())
		) / totalWeight;

		// Determine severity and criticality
		boolean isCritical = determineCriticality(temperatureScore, pressureScore, errorCodeScore, telemetry.errorCodes());
		HealthSeverity severity = determineSeverity(weightedScore, isCritical);

		HealthBreakdown breakdown = new HealthBreakdown(
				temperatureScore,
				pressureScore,
				errorCodeScore,
				violations
		);

		log.debug("Equipment {} health evaluated: score={}, severity={}, critical={}",
				telemetry.equipmentId(), weightedScore, severity, isCritical);

		return EquipmentHealth.builder()
				.correlationId(telemetry.correlationId())
				.equipmentId(telemetry.equipmentId())
				.equipmentType(telemetry.equipmentType())
				.telemetryTimestamp(telemetry.timestamp())
				.healthScore(weightedScore)
				.isCritical(isCritical)
				.severity(severity)
				.breakdown(breakdown)
				.build();
	}

	/**
	 * Evaluates temperature against configured thresholds.
	 *
	 * @return score 0-100 (100 = healthy, 0 = critical)
	 */
	public int evaluateTemperature(Double temperature, List<String> violations) {
		if (temperature == null) {
			return 100; // No temperature data, assume healthy
		}

		var thresholds = config.getThresholds().getTemperature();

		if (temperature >= thresholds.getCritical()) {
			violations.add(String.format("CRITICAL: Temperature %.2f°C exceeds critical threshold %.2f°C",
					temperature, thresholds.getCritical()));
			return 0;
		} else if (temperature >= thresholds.getWarning()) {
			violations.add(String.format("WARNING: Temperature %.2f°C exceeds warning threshold %.2f°C",
					temperature, thresholds.getWarning()));
			// Linear degradation from warning to critical
			double range = thresholds.getCritical() - thresholds.getWarning();
			double excess = temperature - thresholds.getWarning();
			return (int) (50 * (1 - excess / range));
		}
		return 100;
	}

	/**
	 * Evaluates pressure against configured thresholds.
	 *
	 * @return score 0-100 (100 = healthy, 0 = critical)
	 */
	public int evaluatePressure(Double pressure, List<String> violations) {
		if (pressure == null) {
			return 100; // No pressure data, assume healthy
		}

		var thresholds = config.getThresholds().getPressure();

		// Check critical bounds
		if (pressure <= thresholds.getCriticalLow()) {
			violations.add(String.format("CRITICAL: Pressure %.2f below critical low threshold %.2f",
					pressure, thresholds.getCriticalLow()));
			return 0;
		} else if (pressure >= thresholds.getCriticalHigh()) {
			violations.add(String.format("CRITICAL: Pressure %.2f exceeds critical high threshold %.2f",
					pressure, thresholds.getCriticalHigh()));
			return 0;
		}

		// Check warning bounds
		if (pressure <= thresholds.getWarningLow()) {
			violations.add(String.format("WARNING: Pressure %.2f below warning low threshold %.2f",
					pressure, thresholds.getWarningLow()));
			// Linear degradation from warning to critical
			double range = thresholds.getWarningLow() - thresholds.getCriticalLow();
			double deficit = thresholds.getWarningLow() - pressure;
			return (int) (50 * (1 - deficit / range));
		} else if (pressure >= thresholds.getWarningHigh()) {
			violations.add(String.format("WARNING: Pressure %.2f exceeds warning high threshold %.2f",
					pressure, thresholds.getWarningHigh()));
			double range = thresholds.getCriticalHigh() - thresholds.getWarningHigh();
			double excess = pressure - thresholds.getWarningHigh();
			return (int) (50 * (1 - excess / range));
		}

		return 100;
	}

	/**
	 * Evaluates error codes against configured critical/warning lists.
	 *
	 * @return score 0-100 (100 = no errors, 0 = critical error present)
	 */
	public int evaluateErrorCodes(List<String> errorCodes, List<String> violations) {
		if (errorCodes == null || errorCodes.isEmpty()) {
			return 100; // No errors
		}

		var errorConfig = config.getThresholds().getErrorCodes();
		boolean hasCritical = false;
		int warningCount = 0;

		for (String code : errorCodes) {
			if (errorConfig.getCritical().contains(code)) {
				violations.add(String.format("CRITICAL: Error code %s detected", code));
				hasCritical = true;
			} else if (errorConfig.getWarning().contains(code)) {
				violations.add(String.format("WARNING: Error code %s detected", code));
				warningCount++;
			}
		}

		if (hasCritical) {
			return 0;
		} else if (warningCount > 0) {
			// Each warning reduces score by 15 points (minimum 30)
			return Math.max(30, 100 - (warningCount * 15));
		}

		return 100;
	}

	/**
	 * Determines if the telemetry indicates a critical condition.
	 */
	private boolean determineCriticality(int tempScore, int pressureScore, int errorScore, List<String> errorCodes) {
		// Critical if any component score is 0
		if (tempScore == 0 || pressureScore == 0 || errorScore == 0) {
			return true;
		}

		// Critical if any critical error code is present
		if (errorCodes != null) {
			for (String code : errorCodes) {
				if (config.getThresholds().getErrorCodes().getCritical().contains(code)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Determines overall severity based on health score and criticality.
	 */
	private HealthSeverity determineSeverity(int healthScore, boolean isCritical) {
		if (isCritical || healthScore < 30) {
			return HealthSeverity.CRITICAL;
		} else if (healthScore < 70) {
			return HealthSeverity.WARNING;
		}
		return HealthSeverity.HEALTHY;
	}
}
