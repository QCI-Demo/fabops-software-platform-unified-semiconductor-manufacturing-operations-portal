package com.fabops.equipment.processor.engine;

import com.fabops.equipment.processor.config.HealthThresholdProperties;
import com.fabops.equipment.processor.model.EquipmentHealth;
import com.fabops.equipment.processor.model.EquipmentHealth.HealthSeverity;
import com.fabops.equipment.processor.model.Telemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HealthRuleEngineTest {

	private HealthRuleEngine engine;
	private HealthThresholdProperties config;

	@BeforeEach
	void setUp() {
		config = new HealthThresholdProperties();
		// Set up default thresholds
		config.getThresholds().getTemperature().setWarning(75.0);
		config.getThresholds().getTemperature().setCritical(90.0);
		config.getThresholds().getPressure().setWarningLow(0.8);
		config.getThresholds().getPressure().setWarningHigh(1.2);
		config.getThresholds().getPressure().setCriticalLow(0.5);
		config.getThresholds().getPressure().setCriticalHigh(1.5);
		config.getThresholds().getErrorCodes().setCritical(List.of("E001", "E002", "E003"));
		config.getThresholds().getErrorCodes().setWarning(List.of("W001", "W002"));
		config.getScoring().setBaseScore(100);
		config.getScoring().setTemperatureWeight(30);
		config.getScoring().setPressureWeight(30);
		config.getScoring().setErrorCodeWeight(40);

		engine = new HealthRuleEngine(config);
	}

	@Test
	void evaluate_healthyTelemetry_returnsHealthyStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-123",
				"EQ-001",
				"CVD",
				Instant.now(),
				60.0,  // Normal temperature
				1.0,   // Normal pressure
				List.of()  // No errors
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertEquals("corr-123", result.correlationId());
		assertEquals("EQ-001", result.equipmentId());
		assertEquals(100, result.healthScore());
		assertFalse(result.isCritical());
		assertEquals(HealthSeverity.HEALTHY, result.severity());
		assertTrue(result.breakdown().violations().isEmpty());
	}

	@Test
	void evaluate_criticalTemperature_returnsCriticalStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-456",
				"EQ-002",
				"Etcher",
				Instant.now(),
				95.0,  // Critical temperature
				1.0,
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertTrue(result.isCritical());
		assertEquals(HealthSeverity.CRITICAL, result.severity());
		assertEquals(0, result.breakdown().temperatureScore());
		assertTrue(result.breakdown().violations().stream()
				.anyMatch(v -> v.contains("CRITICAL") && v.contains("Temperature")));
	}

	@Test
	void evaluate_warningTemperature_returnsWarningStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-789",
				"EQ-003",
				"Litho",
				Instant.now(),
				80.0,  // Warning temperature (between 75 and 90)
				1.0,
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertFalse(result.isCritical());
		assertEquals(HealthSeverity.WARNING, result.severity());
		assertTrue(result.breakdown().temperatureScore() > 0);
		assertTrue(result.breakdown().temperatureScore() < 100);
	}

	@Test
	void evaluate_criticalPressureLow_returnsCriticalStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-101",
				"EQ-004",
				"CVD",
				Instant.now(),
				60.0,
				0.3,  // Below critical low (0.5)
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertTrue(result.isCritical());
		assertEquals(HealthSeverity.CRITICAL, result.severity());
		assertEquals(0, result.breakdown().pressureScore());
	}

	@Test
	void evaluate_criticalPressureHigh_returnsCriticalStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-102",
				"EQ-005",
				"CVD",
				Instant.now(),
				60.0,
				1.8,  // Above critical high (1.5)
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertTrue(result.isCritical());
		assertEquals(HealthSeverity.CRITICAL, result.severity());
		assertEquals(0, result.breakdown().pressureScore());
	}

	@Test
	void evaluate_criticalErrorCode_returnsCriticalStatus() {
		Telemetry telemetry = Telemetry.of(
				"corr-201",
				"EQ-006",
				"CVD",
				Instant.now(),
				60.0,
				1.0,
				List.of("E001")  // Critical error code
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertTrue(result.isCritical());
		assertEquals(HealthSeverity.CRITICAL, result.severity());
		assertEquals(0, result.breakdown().errorCodeScore());
		assertTrue(result.breakdown().violations().stream()
				.anyMatch(v -> v.contains("CRITICAL") && v.contains("E001")));
	}

	@Test
	void evaluate_warningErrorCodes_reducesScore() {
		Telemetry telemetry = Telemetry.of(
				"corr-202",
				"EQ-007",
				"CVD",
				Instant.now(),
				60.0,
				1.0,
				List.of("W001", "W002")  // Two warning codes
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertFalse(result.isCritical());
		// Two warnings: 100 - 30 = 70
		assertEquals(70, result.breakdown().errorCodeScore());
	}

	@Test
	void evaluate_nullValues_assumesHealthy() {
		Telemetry telemetry = new Telemetry(
				"corr-300",
				"EQ-008",
				"CVD",
				Instant.now(),
				null,  // No temperature
				null,  // No pressure
				null,  // No errors
				null,
				"1.0.0"
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertEquals(100, result.healthScore());
		assertFalse(result.isCritical());
		assertEquals(HealthSeverity.HEALTHY, result.severity());
	}

	@Test
	void evaluate_preservesCorrelationId() {
		String correlationId = "unique-correlation-id-12345";
		Telemetry telemetry = Telemetry.of(
				correlationId,
				"EQ-009",
				"CVD",
				Instant.now(),
				60.0,
				1.0,
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);

		assertEquals(correlationId, result.correlationId());
	}

	@Test
	void evaluate_setsProcessedTimestamp() {
		Instant before = Instant.now();
		Telemetry telemetry = Telemetry.of(
				"corr-400",
				"EQ-010",
				"CVD",
				Instant.now(),
				60.0,
				1.0,
				List.of()
		);

		EquipmentHealth result = engine.evaluate(telemetry);
		Instant after = Instant.now();

		assertNotNull(result.processedAt());
		assertTrue(result.processedAt().isAfter(before) || result.processedAt().equals(before));
		assertTrue(result.processedAt().isBefore(after) || result.processedAt().equals(after));
	}
}
