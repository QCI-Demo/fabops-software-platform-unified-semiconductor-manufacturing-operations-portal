package com.fabops.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for health rule engine thresholds.
 * Values are loaded from application.yml under health.rules prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "health.rules")
public class HealthRuleProperties {

    private TemperatureThresholds temperature = new TemperatureThresholds();
    private PressureThresholds pressure = new PressureThresholds();
    private ErrorCodeConfig errorCodes = new ErrorCodeConfig();
    private WeightConfig weights = new WeightConfig();

    public TemperatureThresholds getTemperature() {
        return temperature;
    }

    public void setTemperature(TemperatureThresholds temperature) {
        this.temperature = temperature;
    }

    public PressureThresholds getPressure() {
        return pressure;
    }

    public void setPressure(PressureThresholds pressure) {
        this.pressure = pressure;
    }

    public ErrorCodeConfig getErrorCodes() {
        return errorCodes;
    }

    public void setErrorCodes(ErrorCodeConfig errorCodes) {
        this.errorCodes = errorCodes;
    }

    public WeightConfig getWeights() {
        return weights;
    }

    public void setWeights(WeightConfig weights) {
        this.weights = weights;
    }

    public static class TemperatureThresholds {
        private double warningThreshold = 75.0;
        private double criticalThreshold = 85.0;
        private double maxThreshold = 95.0;

        public double getWarningThreshold() {
            return warningThreshold;
        }

        public void setWarningThreshold(double warningThreshold) {
            this.warningThreshold = warningThreshold;
        }

        public double getCriticalThreshold() {
            return criticalThreshold;
        }

        public void setCriticalThreshold(double criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }

        public double getMaxThreshold() {
            return maxThreshold;
        }

        public void setMaxThreshold(double maxThreshold) {
            this.maxThreshold = maxThreshold;
        }
    }

    public static class PressureThresholds {
        private double minThreshold = 0.5;
        private double warningLowThreshold = 1.0;
        private double warningHighThreshold = 9.0;
        private double criticalThreshold = 10.0;

        public double getMinThreshold() {
            return minThreshold;
        }

        public void setMinThreshold(double minThreshold) {
            this.minThreshold = minThreshold;
        }

        public double getWarningLowThreshold() {
            return warningLowThreshold;
        }

        public void setWarningLowThreshold(double warningLowThreshold) {
            this.warningLowThreshold = warningLowThreshold;
        }

        public double getWarningHighThreshold() {
            return warningHighThreshold;
        }

        public void setWarningHighThreshold(double warningHighThreshold) {
            this.warningHighThreshold = warningHighThreshold;
        }

        public double getCriticalThreshold() {
            return criticalThreshold;
        }

        public void setCriticalThreshold(double criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }
    }

    public static class ErrorCodeConfig {
        private List<String> criticalCodes = List.of("E001", "E002", "E003", "E010");
        private List<String> warningCodes = List.of("W001", "W002", "W003");

        public List<String> getCriticalCodes() {
            return criticalCodes;
        }

        public void setCriticalCodes(List<String> criticalCodes) {
            this.criticalCodes = criticalCodes;
        }

        public List<String> getWarningCodes() {
            return warningCodes;
        }

        public void setWarningCodes(List<String> warningCodes) {
            this.warningCodes = warningCodes;
        }
    }

    public static class WeightConfig {
        private double temperature = 0.4;
        private double pressure = 0.3;
        private double errorCode = 0.3;

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getPressure() {
            return pressure;
        }

        public void setPressure(double pressure) {
            this.pressure = pressure;
        }

        public double getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(double errorCode) {
            this.errorCode = errorCode;
        }
    }
}
