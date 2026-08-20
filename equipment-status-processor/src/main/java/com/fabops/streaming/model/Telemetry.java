package com.fabops.streaming.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents raw telemetry data consumed from the telemetry.raw topic.
 * Mirrors the RawTelemetryEvent Avro schema.
 */
public class Telemetry {

    private String equipmentId;
    private long timestamp;
    private Map<String, String> payload;
    private String correlationId;
    private String schemaVersion;
    private String sourceSystem;
    private String metricName;
    private String unit;

    public Telemetry() {
    }

    public Telemetry(String equipmentId, long timestamp, Map<String, String> payload,
                     String correlationId, String schemaVersion) {
        this.equipmentId = equipmentId;
        this.timestamp = timestamp;
        this.payload = payload;
        this.correlationId = correlationId;
        this.schemaVersion = schemaVersion;
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

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Telemetry telemetry = (Telemetry) o;
        return timestamp == telemetry.timestamp &&
               Objects.equals(equipmentId, telemetry.equipmentId) &&
               Objects.equals(correlationId, telemetry.correlationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(equipmentId, timestamp, correlationId);
    }

    @Override
    public String toString() {
        return "Telemetry{" +
               "equipmentId='" + equipmentId + '\'' +
               ", timestamp=" + timestamp +
               ", correlationId='" + correlationId + '\'' +
               ", schemaVersion='" + schemaVersion + '\'' +
               '}';
    }
}
