package com.fabops.stream.config;

import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Kafka Streams configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "kafka.streams")
public class KafkaStreamsConfig {

    private String applicationId = "fabops-equipment-stream-processor";
    private String bootstrapServers = "localhost:9092";
    private String defaultKeySerde = "org.apache.kafka.common.serialization.Serdes$StringSerde";
    private String defaultValueSerde = "org.apache.kafka.common.serialization.Serdes$StringSerde";
    private int replicationFactor = 3;
    private String processingGuarantee = "exactly_once_v2";
    private int commitIntervalMs = 1000;
    private long cacheMaxBuffering = 10485760;
    private int numStreamThreads = 2;

    @Bean
    public Properties kafkaStreamsProperties() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, replicationFactor);
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, processingGuarantee);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, commitIntervalMs);
        props.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, cacheMaxBuffering);
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, numStreamThreads);
        
        // Replay-safe configuration
        props.put(StreamsConfig.producerPrefix("acks"), "all");
        props.put(StreamsConfig.producerPrefix("enable.idempotence"), "true");
        
        return props;
    }

    // Getters and Setters
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

    public String getDefaultKeySerde() {
        return defaultKeySerde;
    }

    public void setDefaultKeySerde(String defaultKeySerde) {
        this.defaultKeySerde = defaultKeySerde;
    }

    public String getDefaultValueSerde() {
        return defaultValueSerde;
    }

    public void setDefaultValueSerde(String defaultValueSerde) {
        this.defaultValueSerde = defaultValueSerde;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public String getProcessingGuarantee() {
        return processingGuarantee;
    }

    public void setProcessingGuarantee(String processingGuarantee) {
        this.processingGuarantee = processingGuarantee;
    }

    public int getCommitIntervalMs() {
        return commitIntervalMs;
    }

    public void setCommitIntervalMs(int commitIntervalMs) {
        this.commitIntervalMs = commitIntervalMs;
    }

    public long getCacheMaxBuffering() {
        return cacheMaxBuffering;
    }

    public void setCacheMaxBuffering(long cacheMaxBuffering) {
        this.cacheMaxBuffering = cacheMaxBuffering;
    }

    public int getNumStreamThreads() {
        return numStreamThreads;
    }

    public void setNumStreamThreads(int numStreamThreads) {
        this.numStreamThreads = numStreamThreads;
    }
}
