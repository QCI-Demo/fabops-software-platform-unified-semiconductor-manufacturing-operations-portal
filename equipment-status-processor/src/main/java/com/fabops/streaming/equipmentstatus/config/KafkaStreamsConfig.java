package com.fabops.streaming.equipmentstatus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka Streams configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "kafka.streams")
public class KafkaStreamsConfig {
    
    private String applicationId = "fabops-equipment-status-processor";
    private String bootstrapServers = "localhost:9092";
    private String stateDir = "/tmp/kafka-streams";
    private int replicationFactor = 1;
    private int numStreamThreads = 2;
    private int commitIntervalMs = 1000;
    private String processingGuarantee = "exactly_once_v2";
    
    public String getApplicationId() {
        return applicationId;
    }
    
    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }
    
    public String getBootstrapServers() {
        return bootstrapServers;
    }
    
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }
    
    public String getStateDir() {
        return stateDir;
    }
    
    public void setStateDir(String stateDir) {
        this.stateDir = stateDir;
    }
    
    public int getReplicationFactor() {
        return replicationFactor;
    }
    
    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }
    
    public int getNumStreamThreads() {
        return numStreamThreads;
    }
    
    public void setNumStreamThreads(int numStreamThreads) {
        this.numStreamThreads = numStreamThreads;
    }
    
    public int getCommitIntervalMs() {
        return commitIntervalMs;
    }
    
    public void setCommitIntervalMs(int commitIntervalMs) {
        this.commitIntervalMs = commitIntervalMs;
    }
    
    public String getProcessingGuarantee() {
        return processingGuarantee;
    }
    
    public void setProcessingGuarantee(String processingGuarantee) {
        this.processingGuarantee = processingGuarantee;
    }
}
