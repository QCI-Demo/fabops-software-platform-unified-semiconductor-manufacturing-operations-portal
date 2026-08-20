package com.fabops.equipment.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Kafka topics.
 */
@Configuration
@ConfigurationProperties(prefix = "fabops.topics")
public class TopicProperties {

	private String rawTelemetry = "fabops.telemetry.raw";
	private String equipmentStatus = "fabops.equipment.status";
	private String criticalAlert = "fabops.equipment.critical-alert";

	public String getRawTelemetry() {
		return rawTelemetry;
	}

	public void setRawTelemetry(String rawTelemetry) {
		this.rawTelemetry = rawTelemetry;
	}

	public String getEquipmentStatus() {
		return equipmentStatus;
	}

	public void setEquipmentStatus(String equipmentStatus) {
		this.equipmentStatus = equipmentStatus;
	}

	public String getCriticalAlert() {
		return criticalAlert;
	}

	public void setCriticalAlert(String criticalAlert) {
		this.criticalAlert = criticalAlert;
	}
}
