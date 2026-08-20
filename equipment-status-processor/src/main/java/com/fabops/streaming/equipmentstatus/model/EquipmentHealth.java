package com.fabops.streaming.equipmentstatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Equipment health assessment result computed by the HealthRuleEngine.
 * Contains a health score (0-100) and critical alert flag.
 */
public class EquipmentHealth {
    
    @JsonProperty("equipmentId")
    private final String equipmentId;
    
    @JsonProperty("timestamp")
    private final long timestamp;
    
    @JsonProperty("healthScore")
    private final int healthScore;
    
    @JsonProperty("isCritical")
    private final boolean isCritical;
    
    @JsonProperty("status")
    private final EquipmentStatus status;
    
    @JsonProperty("violations")
    private final List<HealthViolation> violations;
    
    @JsonProperty("correlationId")
    private final String correlationId;
    
    @JsonProperty("schemaVersion")
    private final String schemaVersion;
    
    private EquipmentHealth(Builder builder) {
        this.equipmentId = builder.equipmentId;
        this.timestamp = builder.timestamp;
        this.healthScore = builder.healthScore;
        this.isCritical = builder.isCritical;
        this.status = builder.status;
        this.violations = List.copyOf(builder.violations);
        this.correlationId = builder.correlationId;
        this.schemaVersion = builder.schemaVersion;
    }
    
    public String getEquipmentId() {
        return equipmentId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getHealthScore() {
        return healthScore;
    }
    
    public boolean isCritical() {
        return isCritical;
    }
    
    public EquipmentStatus getStatus() {
        return status;
    }
    
    public List<HealthViolation> getViolations() {
        return violations;
    }
    
    public String getCorrelationId() {
        return correlationId;
    }
    
    public String getSchemaVersion() {
        return schemaVersion;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String equipmentId;
        private long timestamp;
        private int healthScore = 100;
        private boolean isCritical = false;
        private EquipmentStatus status = EquipmentStatus.UNKNOWN;
        private List<HealthViolation> violations = new ArrayList<>();
        private String correlationId;
        private String schemaVersion = "1.0.0";
        
        public Builder equipmentId(String equipmentId) {
            this.equipmentId = equipmentId;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder healthScore(int healthScore) {
            this.healthScore = Math.max(0, Math.min(100, healthScore));
            return this;
        }
        
        public Builder isCritical(boolean isCritical) {
            this.isCritical = isCritical;
            return this;
        }
        
        public Builder status(EquipmentStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder violations(List<HealthViolation> violations) {
            this.violations = new ArrayList<>(violations);
            return this;
        }
        
        public Builder addViolation(HealthViolation violation) {
            this.violations.add(violation);
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }
        
        public EquipmentHealth build() {
            return new EquipmentHealth(this);
        }
    }
    
    @Override
    public String toString() {
        return "EquipmentHealth{" +
                "equipmentId='" + equipmentId + '\'' +
                ", healthScore=" + healthScore +
                ", isCritical=" + isCritical +
                ", status=" + status +
                ", violations=" + violations.size() +
                '}';
    }
}
