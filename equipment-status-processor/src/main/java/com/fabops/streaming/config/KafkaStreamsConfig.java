package com.fabops.streaming.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Kafka Streams configuration for the equipment status processor.
 * Configures connection to Kafka brokers, schema registry, and processing settings.
 */
@Configuration
@ConfigurationProperties(prefix = "kafka")
public class KafkaStreamsConfig {

    private StreamsProperties streams = new StreamsProperties();
    private SchemaRegistryProperties schemaRegistry = new SchemaRegistryProperties();
    private TopicProperties topics = new TopicProperties();

    @Bean
    public Properties kafkaStreamsProperties() {
        Properties props = new Properties();
        
        // Core Kafka Streams settings
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, streams.getApplicationId());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, streams.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        
        // Processing settings
        props.put(StreamsConfig.STATE_DIR_CONFIG, streams.getStateDir());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, streams.getCommitIntervalMs());
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, streams.getReplicationFactor());
        
        // Exactly-once semantics for replay-safety
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        
        // Consumer settings for reliability
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Producer settings for durability
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Schema Registry
        props.put("schema.registry.url", schemaRegistry.getUrl());
        
        return props;
    }

    public StreamsProperties getStreams() {
        return streams;
    }

    public void setStreams(StreamsProperties streams) {
        this.streams = streams;
    }

    public SchemaRegistryProperties getSchemaRegistry() {
        return schemaRegistry;
    }

    public void setSchemaRegistry(SchemaRegistryProperties schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    public TopicProperties getTopics() {
        return topics;
    }

    public void setTopics(TopicProperties topics) {
        this.topics = topics;
    }

    public static class StreamsProperties {
        private String applicationId = "equipment-status-processor";
        private String bootstrapServers = "localhost:9092";
        private int replicationFactor = 3;
        private String stateDir = "/tmp/kafka-streams";
        private int commitIntervalMs = 1000;

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

        public int getReplicationFactor() {
            return replicationFactor;
        }

        public void setReplicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
        }

        public String getStateDir() {
            return stateDir;
        }

        public void setStateDir(String stateDir) {
            this.stateDir = stateDir;
        }

        public int getCommitIntervalMs() {
            return commitIntervalMs;
        }

        public void setCommitIntervalMs(int commitIntervalMs) {
            this.commitIntervalMs = commitIntervalMs;
        }
    }

    public static class SchemaRegistryProperties {
        private String url = "http://localhost:8081";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class TopicProperties {
        private String telemetryRaw = "fabops.telemetry.raw";
        private String equipmentStatus = "fabops.equipment.status";
        private String criticalAlert = "fabops.alerts.critical";

        public String getTelemetryRaw() {
            return telemetryRaw;
        }

        public void setTelemetryRaw(String telemetryRaw) {
            this.telemetryRaw = telemetryRaw;
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
}
