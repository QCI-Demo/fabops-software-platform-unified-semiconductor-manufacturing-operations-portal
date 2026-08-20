package com.fabops.stream;

import com.fabops.stream.engine.HealthRuleEngine;
import com.fabops.stream.engine.HealthRuleEngine.ErrorResult;
import com.fabops.stream.engine.HealthRuleEngine.PressureResult;
import com.fabops.stream.engine.HealthRuleEngine.TemperatureResult;
import com.fabops.stream.model.EquipmentHealth.HealthSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Health Rule Engine.
 */
class HealthRuleEngineTest {

    @Test
    void evaluateTemperature_normal_returnsFullScore() {
        TemperatureResult result = HealthRuleEngine.evaluateTemperature(50.0);
        
        assertEquals("NORMAL", result.status());
        assertEquals(100, result.score());
        assertFalse(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void evaluateTemperature_warning_returnsPartialScore() {
        TemperatureResult result = HealthRuleEngine.evaluateTemperature(82.5);
        
        assertEquals("WARNING", result.status());
        assertTrue(result.score() > 0 && result.score() < 100);
        assertTrue(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void evaluateTemperature_critical_returnsZeroScore() {
        TemperatureResult result = HealthRuleEngine.evaluateTemperature(95.0);
        
        assertEquals("CRITICAL", result.status());
        assertEquals(0, result.score());
        assertFalse(result.isWarning());
        assertTrue(result.isCritical());
    }

    @Test
    void evaluateTemperature_null_returnsUnknown() {
        TemperatureResult result = HealthRuleEngine.evaluateTemperature(null);
        
        assertEquals("UNKNOWN", result.status());
        assertEquals(100, result.score());
    }

    @Test
    void evaluatePressure_normal_returnsFullScore() {
        PressureResult result = HealthRuleEngine.evaluatePressure(1.0);
        
        assertEquals("NORMAL", result.status());
        assertEquals(100, result.score());
        assertFalse(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void evaluatePressure_warningLow_returnsPartialScore() {
        PressureResult result = HealthRuleEngine.evaluatePressure(0.7);
        
        assertEquals("WARNING", result.status());
        assertTrue(result.score() > 0 && result.score() < 100);
        assertTrue(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void evaluatePressure_criticalHigh_returnsZeroScore() {
        PressureResult result = HealthRuleEngine.evaluatePressure(1.6);
        
        assertEquals("CRITICAL", result.status());
        assertEquals(0, result.score());
        assertFalse(result.isWarning());
        assertTrue(result.isCritical());
    }

    @Test
    void evaluateErrorCodes_noErrors_returnsNone() {
        ErrorResult result = HealthRuleEngine.evaluateErrorCodes(List.of());
        
        assertEquals("NONE", result.status());
        assertEquals(100, result.score());
        assertFalse(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void evaluateErrorCodes_criticalCode_returnsCritical() {
        ErrorResult result = HealthRuleEngine.evaluateErrorCodes(List.of("E001"));
        
        assertEquals("CRITICAL", result.status());
        assertEquals(0, result.score());
        assertTrue(result.isCritical());
        assertEquals(List.of("E001"), result.matchedCodes());
    }

    @Test
    void evaluateErrorCodes_warningCode_returnsWarning() {
        ErrorResult result = HealthRuleEngine.evaluateErrorCodes(List.of("W001"));
        
        assertEquals("WARNING", result.status());
        assertTrue(result.score() > 0);
        assertTrue(result.isWarning());
        assertFalse(result.isCritical());
    }

    @Test
    void calculateHealthScore_allNormal_returnsFullScore() {
        TemperatureResult temp = new TemperatureResult("NORMAL", 100, false, false);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        int score = HealthRuleEngine.calculateHealthScoreStatic(temp, pressure, error);
        
        assertEquals(100, score);
    }

    @Test
    void calculateHealthScore_temperatureCritical_returnsLowScore() {
        TemperatureResult temp = new TemperatureResult("CRITICAL", 0, false, true);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        int score = HealthRuleEngine.calculateHealthScoreStatic(temp, pressure, error);
        
        assertEquals(70, score); // 0 + 30 + 40
    }

    @Test
    void determineSeverity_anyCritical_returnsCritical() {
        TemperatureResult temp = new TemperatureResult("CRITICAL", 0, false, true);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        HealthSeverity severity = HealthRuleEngine.determineSeverity(temp, pressure, error, 70);
        
        assertEquals(HealthSeverity.CRITICAL, severity);
    }

    @Test
    void determineSeverity_multipleWarnings_returnsCritical() {
        TemperatureResult temp = new TemperatureResult("WARNING", 50, true, false);
        PressureResult pressure = new PressureResult("WARNING", 50, true, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        HealthSeverity severity = HealthRuleEngine.determineSeverity(temp, pressure, error, 55);
        
        assertEquals(HealthSeverity.CRITICAL, severity);
    }

    @Test
    void determineSeverity_singleWarning_returnsWarning() {
        TemperatureResult temp = new TemperatureResult("WARNING", 50, true, false);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        HealthSeverity severity = HealthRuleEngine.determineSeverity(temp, pressure, error, 85);
        
        assertEquals(HealthSeverity.WARNING, severity);
    }

    @Test
    void determineSeverity_lowHealthScore_returnsCritical() {
        TemperatureResult temp = new TemperatureResult("NORMAL", 100, false, false);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        HealthSeverity severity = HealthRuleEngine.determineSeverity(temp, pressure, error, 35);
        
        assertEquals(HealthSeverity.CRITICAL, severity);
    }

    @Test
    void determineSeverity_allNormal_returnsHealthy() {
        TemperatureResult temp = new TemperatureResult("NORMAL", 100, false, false);
        PressureResult pressure = new PressureResult("NORMAL", 100, false, false);
        ErrorResult error = new ErrorResult("NONE", 100, false, false, List.of());
        
        HealthSeverity severity = HealthRuleEngine.determineSeverity(temp, pressure, error, 100);
        
        assertEquals(HealthSeverity.HEALTHY, severity);
    }
}
