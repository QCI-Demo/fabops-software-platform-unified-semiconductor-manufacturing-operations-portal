package com.fabops.streaming.equipmentstatus.topology;

import com.fabops.streaming.equipmentstatus.config.KafkaStreamsConfig;
import com.fabops.streaming.equipmentstatus.config.TopicConfig;
import com.fabops.streaming.equipmentstatus.engine.HealthRuleEngine;
import com.fabops.streaming.equipmentstatus.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Kafka Streams topology configuration for equipment health processing.
 * 
 * Topology:
 * 1. Reads from raw telemetry topic (fabops.telemetry.raw)
 * 2. Maps telemetry to EquipmentHealth using HealthRuleEngine
 * 3. Branches based on health severity:
 *    - Critical health -> fabops.alerts.critical
 *    - All health statuses -> fabops.equipment.status
 * 
 * Features:
 * - Replay-safe (idempotent processing based on correlation IDs)
 * - Preserves correlation identifiers for tracing
 * - Supports schema evolution via versioned events
 */
@Configuration
public class EquipmentHealthTopology {
    
    private static final Logger logger = LoggerFactory.getLogger(EquipmentHealthTopology.class);
    
    private final TopicConfig topicConfig;
    private final HealthRuleEngine healthRuleEngine;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public EquipmentHealthTopology(TopicConfig topicConfig, 
                                   HealthRuleEngine healthRuleEngine,
                                   ObjectMapper objectMapper) {
        this.topicConfig = topicConfig;
        this.healthRuleEngine = healthRuleEngine;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Build the Kafka Streams topology for equipment health processing.
     */
    @Bean
    public StreamsBuilder streamsBuilder() {
        StreamsBuilder builder = new StreamsBuilder();
        
        // Create Serdes for our domain objects
        Serde<Telemetry> telemetrySerde = createJsonSerde(Telemetry.class);
        Serde<EquipmentHealth> healthSerde = createJsonSerde(EquipmentHealth.class);
        Serde<CriticalAlert> alertSerde = createJsonSerde(CriticalAlert.class);
        
        // Step 1: Read from raw telemetry topic
        KStream<String, Telemetry> telemetryStream = builder.stream(
                topicConfig.getRawTelemetry(),
                Consumed.with(Serdes.String(), telemetrySerde)
                        .withName("source-raw-telemetry")
        );
        
        logger.info("Created stream from topic: {}", topicConfig.getRawTelemetry());
        
        // Step 2: Map telemetry to EquipmentHealth
        KStream<String, EquipmentHealth> healthStream = telemetryStream
                .peek((key, value) -> logger.debug("Processing telemetry for equipment: {}", 
                        value != null ? value.equipmentId() : "null"))
                .filter((key, value) -> value != null && value.equipmentId() != null,
                        Named.as("filter-valid-telemetry"))
                .mapValues((readOnlyKey, telemetry) -> healthRuleEngine.evaluate(telemetry),
                        Named.as("calculate-equipment-health"))
                .peek((key, health) -> logger.debug("Computed health: equipmentId={}, score={}, critical={}",
                        health.getEquipmentId(), health.getHealthScore(), health.isCritical()));
        
        // Step 3: Branch based on health severity
        // Using split() for branching (Kafka Streams 3.x API)
        Map<String, KStream<String, EquipmentHealth>> branches = healthStream.split(Named.as("health-branch-"))
                .branch((key, health) -> health.isCritical(),
                        Branched.as("critical"))
                .defaultBranch(Branched.as("normal"));
        
        KStream<String, EquipmentHealth> criticalStream = branches.get("health-branch-critical");
        KStream<String, EquipmentHealth> normalStream = branches.get("health-branch-normal");
        
        // Step 4: All health statuses go to equipment-status topic (for log compaction)
        healthStream
                .selectKey((key, health) -> health.getEquipmentId(),
                        Named.as("rekey-by-equipment-id"))
                .to(topicConfig.getEquipmentStatus(),
                        Produced.with(Serdes.String(), healthSerde)
                                .withName("sink-equipment-status"));
        
        logger.info("Configured sink to equipment status topic: {}", topicConfig.getEquipmentStatus());
        
        // Step 5: Critical health events generate alerts
        if (criticalStream != null) {
            criticalStream
                    .mapValues((readOnlyKey, health) -> CriticalAlert.fromCriticalHealth(health),
                            Named.as("create-critical-alert"))
                    .peek((key, alert) -> logger.warn("Critical alert generated: equipmentId={}, alertId={}",
                            alert.equipmentId(), alert.alertId()))
                    .to(topicConfig.getCriticalAlert(),
                            Produced.with(Serdes.String(), alertSerde)
                                    .withName("sink-critical-alert"));
            
            logger.info("Configured sink to critical alert topic: {}", topicConfig.getCriticalAlert());
        }
        
        return builder;
    }
    
    /**
     * Create a JSON Serde for the given class using Spring Kafka serializers.
     */
    private <T> Serde<T> createJsonSerde(Class<T> clazz) {
        JsonSerializer<T> serializer = new JsonSerializer<>(objectMapper);
        JsonDeserializer<T> deserializer = new JsonDeserializer<>(clazz, objectMapper);
        deserializer.setRemoveTypeHeaders(true);
        deserializer.addTrustedPackages("com.fabops.streaming.equipmentstatus.model");
        deserializer.setUseTypeMapperForKey(false);
        
        return Serdes.serdeFrom(serializer, deserializer);
    }
}
