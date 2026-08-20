package com.fabops.streaming;

import com.fabops.streaming.config.HealthRuleProperties;
import com.fabops.streaming.engine.HealthRuleEngine;
import com.fabops.streaming.model.EquipmentHealth;
import com.fabops.streaming.model.Telemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthRuleEngine.
 */
class HealthRuleEngineTest {

    private HealthRuleEngine engine;
    private HealthRuleProperties properties;

    @BeforeEach
    void setUp() {
        properties = createDefaultProperties();
        engine = new HealthRuleEngine(properties);
    }

    private HealthRuleProperties createDefaultProperties() {
        HealthRuleProperties props = new HealthRuleProperties();
        
        HealthRuleProperties.TemperatureThresholds temp = new HealthRuleProperties.TemperatureThresholds();
        temp.setWarningThreshold(75.0);
        temp.setCriticalThreshold(85.0);
        temp.setMaxThreshold(95.0);
        props.setTemperature(temp);
        
        HealthRuleProperties.PressureThresholds pressure = new HealthRuleProperties.PressureThresholds();
        pressure.setMinThreshold(0.5);
        pressure.setWarningLowThreshold(1.0);
        pressure.setWarningHighThreshold(9.0);
        pressure.setCriticalThreshold(10.0);
        props.setPressure(pressure);
        
        HealthRuleProperties.ErrorCodeConfig errorCodes = new HealthRuleProperties.ErrorCodeConfig();
        errorCodes.setCriticalCodes(List.of("E001", "E002", "E003", "E010"));
        errorCodes.setWarningCodes(List.of("W001", "W002", "W003"));
        props.setErrorCodes(errorCodes);
        
        HealthRuleProperties.WeightConfig weights = new HealthRuleProperties.WeightConfig();
        weights.setTemperature(0.4);
        weights.setPressure(0.3);
        weights.setErrorCode(0.3);
        props.setWeights(weights);
        
        return props;
    }

    private Telemetry createTelemetry(Map<String, String> payload) {
        Telemetry telemetry = new Telemetry();
        telemetry.setEquipmentId("EQUIP-001");
        telemetry.setTimestamp(System.currentTimeMillis());
        telemetry.setPayload(payload);
        telemetry.setCorrelationId(UUID.randomUUID().toString());
        telemetry.setSchemaVersion("1.0.0");
        return telemetry;
    }

    @Nested
    @DisplayName("Temperature Evaluation Tests")
    class TemperatureTests {

        @Test
        @DisplayName("Normal temperature returns healthy score")
        void normalTemperature() {
            Map<String, String> payload = Map.of("temperature", "50.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertEquals(100, health.getHealthScore());
            assertFalse(health.isCritical());
            assertEquals(EquipmentHealth.HealthStatus.HEALTHY, health.getStatus());
        }

        @Test
        @DisplayName("Warning temperature returns reduced score")
        void warningTemperature() {
            Map<String, String> payload = Map.of("temperature", "80.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.getHealthScore() < 100);
            assertFalse(health.isCritical());
        }

        @Test
        @DisplayName("Critical temperature flags as critical")
        void criticalTemperature() {
            Map<String, String> payload = Map.of("temperature", "90.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
            assertEquals(EquipmentHealth.HealthStatus.CRITICAL, health.getStatus());
            assertNotNull(health.getAlertTitle());
            assertNotNull(health.getAlertDescription());
        }

        @Test
        @DisplayName("Maximum temperature returns zero score")
        void maxTemperature() {
            Map<String, String> payload = Map.of("temperature", "95.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
            assertTrue(health.getHealthScore() < 40);
        }
    }

    @Nested
    @DisplayName("Pressure Evaluation Tests")
    class PressureTests {

        @Test
        @DisplayName("Normal pressure returns healthy score")
        void normalPressure() {
            Map<String, String> payload = Map.of("pressure", "5.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertEquals(100, health.getHealthScore());
            assertFalse(health.isCritical());
        }

        @Test
        @DisplayName("Low pressure warning reduces score")
        void lowPressureWarning() {
            Map<String, String> payload = Map.of("pressure", "0.8");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.getHealthScore() < 100);
        }

        @Test
        @DisplayName("Critical low pressure flags as critical")
        void criticalLowPressure() {
            Map<String, String> payload = Map.of("pressure", "0.3");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
            assertEquals(EquipmentHealth.HealthStatus.CRITICAL, health.getStatus());
        }

        @Test
        @DisplayName("Critical high pressure flags as critical")
        void criticalHighPressure() {
            Map<String, String> payload = Map.of("pressure", "10.5");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
        }
    }

    @Nested
    @DisplayName("Error Code Evaluation Tests")
    class ErrorCodeTests {

        @Test
        @DisplayName("No error code returns healthy score")
        void noErrorCode() {
            Map<String, String> payload = Map.of("temperature", "50.0");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertEquals(100, health.getHealthScore());
            assertFalse(health.isCritical());
        }

        @Test
        @DisplayName("Critical error code flags as critical")
        void criticalErrorCode() {
            Map<String, String> payload = new HashMap<>();
            payload.put("errorCode", "E001");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
            assertEquals(EquipmentHealth.HealthStatus.CRITICAL, health.getStatus());
            assertTrue(health.getAlertTitle().contains("E001"));
        }

        @Test
        @DisplayName("Warning error code reduces score")
        void warningErrorCode() {
            Map<String, String> payload = new HashMap<>();
            payload.put("errorCode", "W001");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.getHealthScore() < 100);
            assertFalse(health.isCritical());
        }
    }

    @Nested
    @DisplayName("Combined Evaluation Tests")
    class CombinedTests {

        @Test
        @DisplayName("Multiple warnings combine to reduce overall score")
        void multipleWarnings() {
            Map<String, String> payload = new HashMap<>();
            payload.put("temperature", "80.0");
            payload.put("pressure", "9.5");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.getHealthScore() < 100);
            assertEquals(EquipmentHealth.HealthStatus.WARNING, health.getStatus());
        }

        @Test
        @DisplayName("Any critical condition flags overall as critical")
        void anyCriticalFlagsOverall() {
            Map<String, String> payload = new HashMap<>();
            payload.put("temperature", "50.0");  // Normal
            payload.put("pressure", "5.0");       // Normal
            payload.put("errorCode", "E001");     // Critical
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertTrue(health.isCritical());
        }

        @Test
        @DisplayName("Correlation IDs are preserved")
        void correlationIdsPreserved() {
            String sourceCorrelationId = UUID.randomUUID().toString();
            Telemetry telemetry = createTelemetry(Map.of("temperature", "50.0"));
            telemetry.setCorrelationId(sourceCorrelationId);
            
            EquipmentHealth health = engine.evaluate(telemetry);
            
            assertEquals(sourceCorrelationId, health.getSourceEventCorrelationId());
            assertNotNull(health.getCorrelationId());
            assertNotEquals(sourceCorrelationId, health.getCorrelationId());
        }

        @Test
        @DisplayName("Equipment ID is preserved")
        void equipmentIdPreserved() {
            Telemetry telemetry = createTelemetry(Map.of("temperature", "50.0"));
            telemetry.setEquipmentId("SPECIAL-EQUIP-123");
            
            EquipmentHealth health = engine.evaluate(telemetry);
            
            assertEquals("SPECIAL-EQUIP-123", health.getEquipmentId());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null payload returns healthy score")
        void nullPayload() {
            Telemetry telemetry = createTelemetry(null);
            EquipmentHealth health = engine.evaluate(telemetry);
            
            assertEquals(100, health.getHealthScore());
            assertFalse(health.isCritical());
        }

        @Test
        @DisplayName("Empty payload returns healthy score")
        void emptyPayload() {
            Telemetry telemetry = createTelemetry(Map.of());
            EquipmentHealth health = engine.evaluate(telemetry);
            
            assertEquals(100, health.getHealthScore());
            assertFalse(health.isCritical());
        }

        @Test
        @DisplayName("Invalid numeric value is ignored")
        void invalidNumericValue() {
            Map<String, String> payload = Map.of("temperature", "not-a-number");
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertEquals(100, health.getHealthScore());
        }

        @Test
        @DisplayName("Health details contain metrics")
        void healthDetailsContainMetrics() {
            Map<String, String> payload = new HashMap<>();
            payload.put("temperature", "80.0");
            payload.put("pressure", "5.0");
            payload.put("errorCode", "W001");
            
            EquipmentHealth health = engine.evaluate(createTelemetry(payload));
            
            assertNotNull(health.getDetails());
            assertEquals("80.0", health.getDetails().get("temperature"));
            assertEquals("5.0", health.getDetails().get("pressure"));
            assertEquals("W001", health.getDetails().get("errorCode"));
        }
    }
}
