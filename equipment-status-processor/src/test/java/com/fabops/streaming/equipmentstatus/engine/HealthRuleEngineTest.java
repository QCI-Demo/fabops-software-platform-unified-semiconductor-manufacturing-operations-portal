package com.fabops.streaming.equipmentstatus.engine;

import com.fabops.streaming.equipmentstatus.config.HealthThresholdsConfig;
import com.fabops.streaming.equipmentstatus.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HealthRuleEngine.
 */
class HealthRuleEngineTest {
    
    private HealthRuleEngine engine;
    private HealthThresholdsConfig config;
    
    @BeforeEach
    void setUp() {
        config = new HealthThresholdsConfig();
        engine = new HealthRuleEngine(config);
    }
    
    @Test
    void testHealthyTelemetryProducesFullScore() {
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "75.0",
                "pressure", "1.0",
                "vibration", "2.0",
                "humidity", "50.0"
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(100, health.getHealthScore());
        assertFalse(health.isCritical());
        assertTrue(health.getViolations().isEmpty());
        assertEquals(EquipmentStatus.RUNNING, health.getStatus());
    }
    
    @Test
    void testTemperatureWarningReducesScore() {
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "90.0"  // Above 85 warning threshold
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(85, health.getHealthScore()); // 100 - 15 penalty
        assertFalse(health.isCritical());
        assertEquals(1, health.getViolations().size());
        assertEquals(HealthViolation.Severity.WARNING, health.getViolations().get(0).severity());
    }
    
    @Test
    void testTemperatureCriticalReducesScoreAndFlags() {
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "100.0"  // Above 95 critical threshold
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(60, health.getHealthScore()); // 100 - 40 penalty
        assertTrue(health.isCritical());
        assertEquals(1, health.getViolations().size());
        assertEquals(HealthViolation.Severity.CRITICAL, health.getViolations().get(0).severity());
    }
    
    @Test
    void testPressureCriticalLowReducesScore() {
        Telemetry telemetry = createTelemetry(Map.of(
                "pressure", "0.3"  // Below 0.5 critical low
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(70, health.getHealthScore()); // 100 - 30 penalty
        assertTrue(health.isCritical());
    }
    
    @Test
    void testPressureCriticalHighReducesScore() {
        Telemetry telemetry = createTelemetry(Map.of(
                "pressure", "2.0"  // Above 1.5 critical high
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(70, health.getHealthScore());
        assertTrue(health.isCritical());
    }
    
    @Test
    void testCriticalErrorCodeReducesScore() {
        Telemetry telemetry = createTelemetry(Map.of(
                "errorCode", "E001"  // Critical error code
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals(50, health.getHealthScore()); // 100 - 50 penalty
        assertTrue(health.isCritical());
    }
    
    @Test
    void testMultipleViolationsAccumulate() {
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "100.0",  // Critical: -40
                "pressure", "0.3",       // Critical: -30
                "errorCode", "FAULT"     // Critical: -50
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        // Score should be clamped to 0: 100 - 40 - 30 - 50 = -20 -> 0
        assertEquals(0, health.getHealthScore());
        assertTrue(health.isCritical());
        assertEquals(3, health.getViolations().size());
        assertEquals(EquipmentStatus.DOWN, health.getStatus());
    }
    
    @Test
    void testScoreBelowThresholdMarksAsCritical() {
        // Even without a CRITICAL violation, score below threshold is critical
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "90.0",   // Warning: -15
                "pressure", "0.7",       // Warning: -10
                "vibration", "6.0",      // Warning: -10
                "humidity", "25.0"       // Warning: -5
        ));
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        // 100 - 15 - 10 - 10 - 5 = 60, still above 50 threshold
        assertEquals(60, health.getHealthScore());
        assertFalse(health.isCritical());
    }
    
    @Test
    void testCorrelationIdPreserved() {
        Map<String, String> payload = new HashMap<>();
        payload.put("temperature", "75.0");
        
        Telemetry telemetry = new Telemetry(
                "EQUIP-001",
                System.currentTimeMillis(),
                payload,
                "corr-12345",
                "1.0.0",
                "test-system",
                null,
                null
        );
        
        EquipmentHealth health = engine.evaluate(telemetry);
        
        assertEquals("corr-12345", health.getCorrelationId());
        assertEquals("EQUIP-001", health.getEquipmentId());
    }
    
    @Test
    void testStaticEvaluateWithDefaults() {
        Telemetry telemetry = createTelemetry(Map.of(
                "temperature", "100.0"
        ));
        
        EquipmentHealth health = HealthRuleEngine.evaluateWithDefaults(telemetry);
        
        assertTrue(health.isCritical());
        assertNotNull(health.getSchemaVersion());
    }
    
    private Telemetry createTelemetry(Map<String, String> payload) {
        return new Telemetry(
                "EQUIP-TEST",
                System.currentTimeMillis(),
                new HashMap<>(payload),
                "test-correlation-id",
                "1.0.0",
                "test-source",
                null,
                null
        );
    }
}
