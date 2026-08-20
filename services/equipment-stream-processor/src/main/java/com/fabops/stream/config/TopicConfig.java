package com.fabops.stream.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Topic configuration for Kafka Streams.
 */
@Configuration
@ConfigurationProperties(prefix = "topics")
public class TopicConfig {

    private TopicProperties telemetryRaw = new TopicProperties();
    private TopicProperties equipmentStatus = new TopicProperties();
    private TopicProperties criticalAlerts = new TopicProperties();

    public TopicProperties getTelemetryRaw() {
        return telemetryRaw;
    }

    public void setTelemetryRaw(TopicProperties telemetryRaw) {
        this.telemetryRaw = telemetryRaw;
    }

    public TopicProperties getEquipmentStatus() {
        return equipmentStatus;
    }

    public void setEquipmentStatus(TopicProperties equipmentStatus) {
        this.equipmentStatus = equipmentStatus;
    }

    public TopicProperties getCriticalAlerts() {
        return criticalAlerts;
    }

    public void setCriticalAlerts(TopicProperties criticalAlerts) {
        this.criticalAlerts = criticalAlerts;
    }

    public static class TopicProperties {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
