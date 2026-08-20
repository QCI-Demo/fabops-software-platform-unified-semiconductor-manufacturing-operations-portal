package com.fabops.streaming.equipmentstatus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.ArrayList;

/**
 * Configuration properties for health rule engine thresholds.
 * Loaded from application.yml under fabops.health prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "fabops.health")
public class HealthThresholdsConfig {
    
    private Thresholds thresholds = new Thresholds();
    private Scoring scoring = new Scoring();
    
    public Thresholds getThresholds() {
        return thresholds;
    }
    
    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }
    
    public Scoring getScoring() {
        return scoring;
    }
    
    public void setScoring(Scoring scoring) {
        this.scoring = scoring;
    }
    
    public static class Thresholds {
        private Temperature temperature = new Temperature();
        private Pressure pressure = new Pressure();
        private ErrorCode errorCode = new ErrorCode();
        private Vibration vibration = new Vibration();
        private Humidity humidity = new Humidity();
        
        public Temperature getTemperature() {
            return temperature;
        }
        
        public void setTemperature(Temperature temperature) {
            this.temperature = temperature;
        }
        
        public Pressure getPressure() {
            return pressure;
        }
        
        public void setPressure(Pressure pressure) {
            this.pressure = pressure;
        }
        
        public ErrorCode getErrorCode() {
            return errorCode;
        }
        
        public void setErrorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
        }
        
        public Vibration getVibration() {
            return vibration;
        }
        
        public void setVibration(Vibration vibration) {
            this.vibration = vibration;
        }
        
        public Humidity getHumidity() {
            return humidity;
        }
        
        public void setHumidity(Humidity humidity) {
            this.humidity = humidity;
        }
    }
    
    public static class Temperature {
        private double warning = 85.0;
        private double critical = 95.0;
        
        public double getWarning() {
            return warning;
        }
        
        public void setWarning(double warning) {
            this.warning = warning;
        }
        
        public double getCritical() {
            return critical;
        }
        
        public void setCritical(double critical) {
            this.critical = critical;
        }
    }
    
    public static class Pressure {
        private double warningLow = 0.8;
        private double warningHigh = 1.2;
        private double criticalLow = 0.5;
        private double criticalHigh = 1.5;
        
        public double getWarningLow() {
            return warningLow;
        }
        
        public void setWarningLow(double warningLow) {
            this.warningLow = warningLow;
        }
        
        public double getWarningHigh() {
            return warningHigh;
        }
        
        public void setWarningHigh(double warningHigh) {
            this.warningHigh = warningHigh;
        }
        
        public double getCriticalLow() {
            return criticalLow;
        }
        
        public void setCriticalLow(double criticalLow) {
            this.criticalLow = criticalLow;
        }
        
        public double getCriticalHigh() {
            return criticalHigh;
        }
        
        public void setCriticalHigh(double criticalHigh) {
            this.criticalHigh = criticalHigh;
        }
    }
    
    public static class ErrorCode {
        private List<String> criticalCodes = new ArrayList<>(List.of("E001", "E002", "E003", "FAULT", "EMERGENCY"));
        
        public List<String> getCriticalCodes() {
            return criticalCodes;
        }
        
        public void setCriticalCodes(List<String> criticalCodes) {
            this.criticalCodes = criticalCodes;
        }
    }
    
    public static class Vibration {
        private double warning = 5.0;
        private double critical = 10.0;
        
        public double getWarning() {
            return warning;
        }
        
        public void setWarning(double warning) {
            this.warning = warning;
        }
        
        public double getCritical() {
            return critical;
        }
        
        public void setCritical(double critical) {
            this.critical = critical;
        }
    }
    
    public static class Humidity {
        private double warningLow = 30.0;
        private double warningHigh = 70.0;
        private double criticalLow = 20.0;
        private double criticalHigh = 80.0;
        
        public double getWarningLow() {
            return warningLow;
        }
        
        public void setWarningLow(double warningLow) {
            this.warningLow = warningLow;
        }
        
        public double getWarningHigh() {
            return warningHigh;
        }
        
        public void setWarningHigh(double warningHigh) {
            this.warningHigh = warningHigh;
        }
        
        public double getCriticalLow() {
            return criticalLow;
        }
        
        public void setCriticalLow(double criticalLow) {
            this.criticalLow = criticalLow;
        }
        
        public double getCriticalHigh() {
            return criticalHigh;
        }
        
        public void setCriticalHigh(double criticalHigh) {
            this.criticalHigh = criticalHigh;
        }
    }
    
    public static class Scoring {
        private int baseScore = 100;
        private int temperatureWarningPenalty = 15;
        private int temperatureCriticalPenalty = 40;
        private int pressureWarningPenalty = 10;
        private int pressureCriticalPenalty = 30;
        private int errorCodeCriticalPenalty = 50;
        private int vibrationWarningPenalty = 10;
        private int vibrationCriticalPenalty = 25;
        private int humidityWarningPenalty = 5;
        private int humidityCriticalPenalty = 15;
        private int criticalThreshold = 50;
        
        public int getBaseScore() {
            return baseScore;
        }
        
        public void setBaseScore(int baseScore) {
            this.baseScore = baseScore;
        }
        
        public int getTemperatureWarningPenalty() {
            return temperatureWarningPenalty;
        }
        
        public void setTemperatureWarningPenalty(int temperatureWarningPenalty) {
            this.temperatureWarningPenalty = temperatureWarningPenalty;
        }
        
        public int getTemperatureCriticalPenalty() {
            return temperatureCriticalPenalty;
        }
        
        public void setTemperatureCriticalPenalty(int temperatureCriticalPenalty) {
            this.temperatureCriticalPenalty = temperatureCriticalPenalty;
        }
        
        public int getPressureWarningPenalty() {
            return pressureWarningPenalty;
        }
        
        public void setPressureWarningPenalty(int pressureWarningPenalty) {
            this.pressureWarningPenalty = pressureWarningPenalty;
        }
        
        public int getPressureCriticalPenalty() {
            return pressureCriticalPenalty;
        }
        
        public void setPressureCriticalPenalty(int pressureCriticalPenalty) {
            this.pressureCriticalPenalty = pressureCriticalPenalty;
        }
        
        public int getErrorCodeCriticalPenalty() {
            return errorCodeCriticalPenalty;
        }
        
        public void setErrorCodeCriticalPenalty(int errorCodeCriticalPenalty) {
            this.errorCodeCriticalPenalty = errorCodeCriticalPenalty;
        }
        
        public int getVibrationWarningPenalty() {
            return vibrationWarningPenalty;
        }
        
        public void setVibrationWarningPenalty(int vibrationWarningPenalty) {
            this.vibrationWarningPenalty = vibrationWarningPenalty;
        }
        
        public int getVibrationCriticalPenalty() {
            return vibrationCriticalPenalty;
        }
        
        public void setVibrationCriticalPenalty(int vibrationCriticalPenalty) {
            this.vibrationCriticalPenalty = vibrationCriticalPenalty;
        }
        
        public int getHumidityWarningPenalty() {
            return humidityWarningPenalty;
        }
        
        public void setHumidityWarningPenalty(int humidityWarningPenalty) {
            this.humidityWarningPenalty = humidityWarningPenalty;
        }
        
        public int getHumidityCriticalPenalty() {
            return humidityCriticalPenalty;
        }
        
        public void setHumidityCriticalPenalty(int humidityCriticalPenalty) {
            this.humidityCriticalPenalty = humidityCriticalPenalty;
        }
        
        public int getCriticalThreshold() {
            return criticalThreshold;
        }
        
        public void setCriticalThreshold(int criticalThreshold) {
            this.criticalThreshold = criticalThreshold;
        }
    }
}
