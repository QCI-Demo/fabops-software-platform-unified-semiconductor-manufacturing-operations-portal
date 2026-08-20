package com.fabops.equipment.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for health thresholds and scoring.
 * Externalizes all threshold values to application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "fabops.health")
public class HealthThresholdProperties {

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
		private ErrorCodes errorCodes = new ErrorCodes();

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

		public ErrorCodes getErrorCodes() {
			return errorCodes;
		}

		public void setErrorCodes(ErrorCodes errorCodes) {
			this.errorCodes = errorCodes;
		}
	}

	public static class Temperature {
		private double warning = 75.0;
		private double critical = 90.0;

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

	public static class ErrorCodes {
		private List<String> critical = List.of("E001", "E002", "E003", "E010", "E020");
		private List<String> warning = List.of("W001", "W002", "W003");

		public List<String> getCritical() {
			return critical;
		}

		public void setCritical(List<String> critical) {
			this.critical = critical;
		}

		public List<String> getWarning() {
			return warning;
		}

		public void setWarning(List<String> warning) {
			this.warning = warning;
		}
	}

	public static class Scoring {
		private int baseScore = 100;
		private int temperatureWeight = 30;
		private int pressureWeight = 30;
		private int errorCodeWeight = 40;

		public int getBaseScore() {
			return baseScore;
		}

		public void setBaseScore(int baseScore) {
			this.baseScore = baseScore;
		}

		public int getTemperatureWeight() {
			return temperatureWeight;
		}

		public void setTemperatureWeight(int temperatureWeight) {
			this.temperatureWeight = temperatureWeight;
		}

		public int getPressureWeight() {
			return pressureWeight;
		}

		public void setPressureWeight(int pressureWeight) {
			this.pressureWeight = pressureWeight;
		}

		public int getErrorCodeWeight() {
			return errorCodeWeight;
		}

		public void setErrorCodeWeight(int errorCodeWeight) {
			this.errorCodeWeight = errorCodeWeight;
		}
	}
}
