package com.fabops.streaming.model;

import java.util.Map;
import java.util.Objects;

/**
 * Equipment health assessment result produced by the HealthRuleEngine.
 * Contains health score (0-100), criticality flag, and contributing factors.
 */
public class EquipmentHealth {

    private String equipmentId;
    private long timestamp;
    private int healthScore;
    private boolean isCritical;
    private String correlationId;
    private String sourceEventCorrelationId;
    private HealthStatus status;
    private String alertTitle;
    private String alertDescription;
    private Map<String, String> details;
    private String schemaVersion;

    public EquipmentHealth() {
        this.schemaVersion = "1.0.0";
    }

    public EquipmentHealth(String equipmentId, long timestamp, int healthScore, 
                           boolean isCritical, String correlationId) {
        this.equipmentId = equipmentId;
        this.timestamp = timestamp;
        this.healthScore = healthScore;
        this.isCritical = isCritical;
        this.correlationId = correlationId;
        this.schemaVersion = "1.0.0";
    }

    public enum HealthStatus {
        HEALTHY,
        WARNING,
        CRITICAL,
        UNKNOWN
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(String equipmentId) {
        this.equipmentId = equipmentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(int healthScore) {
        this.healthScore = healthScore;
    }

    public boolean isCritical() {
        return isCritical;
    }

    public void setCritical(boolean critical) {
        isCritical = critical;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSourceEventCorrelationId() {
        return sourceEventCorrelationId;
    }

    public void setSourceEventCorrelationId(String sourceEventCorrelationId) {
        this.sourceEventCorrelationId = sourceEventCorrelationId;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public String getAlertTitle() {
        return alertTitle;
    }

    public void setAlertTitle(String alertTitle) {
        this.alertTitle = alertTitle;
    }

    public String getAlertDescription() {
        return alertDescription;
    }

    public void setAlertDescription(String alertDescription) {
        this.alertDescription = alertDescription;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EquipmentHealth that = (EquipmentHealth) o;
        return timestamp == that.timestamp &&
               healthScore == that.healthScore &&
               isCritical == that.isCritical &&
               Objects.equals(equipmentId, that.equipmentId) &&
               Objects.equals(correlationId, that.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(equipmentId, timestamp, healthScore, isCritical, correlationId);
    }

    @Override
    public String toString() {
        return "EquipmentHealth{" +
               "equipmentId='" + equipmentId + '\'' +
               ", healthScore=" + healthScore +
               ", isCritical=" + isCritical +
               ", status=" + status +
               ", correlationId='" + correlationId + '\'' +
               '}';
    }
}
