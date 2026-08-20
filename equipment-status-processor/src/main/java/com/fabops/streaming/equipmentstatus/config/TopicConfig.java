package com.fabops.streaming.equipmentstatus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Topic configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "fabops.topics")
public class TopicConfig {
    
    private String rawTelemetry = "fabops.telemetry.raw";
    private String equipmentStatus = "fabops.equipment.status";
    private String criticalAlert = "fabops.alerts.critical";
    
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
