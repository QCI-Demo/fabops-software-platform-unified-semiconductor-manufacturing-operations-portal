package com.fabops.stream.topology;

import com.fabops.stream.config.TopicConfig;
import com.fabops.stream.engine.HealthRuleEngine;
import com.fabops.stream.model.CriticalAlert;
import com.fabops.stream.model.EquipmentHealth;
import com.fabops.stream.model.EquipmentHealth.HealthSeverity;
import com.fabops.stream.model.Telemetry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Kafka Streams topology for equipment health calculation.
 * 
 * This topology:
 * 1. Reads from raw telemetry topic
 * 2. Transforms telemetry to equipment health via rule engine
 * 3. Branches based on severity:
 *    - All health events go to equipment status topic
 *    - Critical events additionally go to critical alerts topic
 * 
 * The topology is replay-safe through exactly-once processing semantics.
 * Correlation identifiers are preserved throughout the pipeline.
 */
@Configuration
public class EquipmentHealthTopology {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentHealthTopology.class);
    
    private static final String BRANCH_CRITICAL = "critical-";
    private static final String BRANCH_WARNING = "warning-";
    private static final String BRANCH_HEALTHY = "healthy-";
    
    private final HealthRuleEngine healthRuleEngine;
    private final TopicConfig topicConfig;
    private final ObjectMapper objectMapper;

    public EquipmentHealthTopology(HealthRuleEngine healthRuleEngine, TopicConfig topicConfig) {
        this.healthRuleEngine = healthRuleEngine;
        this.topicConfig = topicConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Builds the Kafka Streams topology.
     * 
     * @param builder the StreamsBuilder instance
     * @return the configured KStream for testing purposes
     */
    @Bean
    public KStream<String, String> buildTopology(StreamsBuilder builder) {
        logger.info("Building equipment health stream topology");
        
        // 1. Read from raw telemetry topic
        KStream<String, String> source = builder.stream(
                topicConfig.getTelemetryRaw().getName(),
                Consumed.with(Serdes.String(), Serdes.String())
                        .withName("source-telemetry-raw")
        );
        
        logger.info("Consuming from topic: {}", topicConfig.getTelemetryRaw().getName());
        
        // 2. Deserialize telemetry and calculate health
        KStream<String, EquipmentHealthWithTelemetry> healthStream = source
                .peek((key, value) -> logger.debug("Received telemetry: key={}", key))
                .mapValues((key, value) -> {
                    try {
                        Telemetry telemetry = objectMapper.readValue(value, Telemetry.class);
                        EquipmentHealth health = healthRuleEngine.evaluate(telemetry);
                        return new EquipmentHealthWithTelemetry(health, telemetry);
                    } catch (Exception e) {
                        logger.error("Failed to process telemetry: {}", e.getMessage());
                        return null;
                    }
                }, Named.as("calculate-health"))
                .filter((key, value) -> value != null, Named.as("filter-null-health"));
        
        // 3. Send all health events to equipment status topic
        healthStream
                .mapValues((key, combo) -> serializeHealth(combo.health()), Named.as("serialize-health"))
                .filter((key, value) -> value != null)
                .to(topicConfig.getEquipmentStatus().getName(), 
                    Produced.with(Serdes.String(), Serdes.String())
                            .withName("sink-equipment-status"));
        
        logger.info("Producing to status topic: {}", topicConfig.getEquipmentStatus().getName());
        
        // 4. Branch based on severity and send critical alerts
        Map<String, KStream<String, EquipmentHealthWithTelemetry>> branches = healthStream
                .split(Named.as("severity-branch"))
                .branch((key, combo) -> combo.health().severity() == HealthSeverity.CRITICAL,
                        Branched.as(BRANCH_CRITICAL))
                .branch((key, combo) -> combo.health().severity() == HealthSeverity.WARNING,
                        Branched.as(BRANCH_WARNING))
                .defaultBranch(Branched.as(BRANCH_HEALTHY));
        
        // 5. Process critical branch - create and emit alerts
        KStream<String, EquipmentHealthWithTelemetry> criticalStream = 
                branches.get("severity-branch" + BRANCH_CRITICAL);
        
        if (criticalStream != null) {
            criticalStream
                    .mapValues((key, combo) -> {
                        CriticalAlert alert = healthRuleEngine.createCriticalAlert(
                                combo.health(), combo.telemetry());
                        return serializeAlert(alert);
                    }, Named.as("create-critical-alert"))
                    .filter((key, value) -> value != null, Named.as("filter-null-alerts"))
                    .peek((key, value) -> logger.warn("Emitting critical alert for equipment: {}", key))
                    .to(topicConfig.getCriticalAlerts().getName(),
                        Produced.with(Serdes.String(), Serdes.String())
                                .withName("sink-critical-alerts"));
            
            logger.info("Producing to alerts topic: {}", topicConfig.getCriticalAlerts().getName());
        }
        
        // Log branch metrics
        branches.forEach((branchName, stream) -> {
            if (stream != null) {
                stream.peek((key, value) -> 
                    logger.debug("Branch {}: equipment={}, score={}", 
                            branchName, value.health().equipmentId(), value.health().healthScore()));
            }
        });
        
        return source;
    }

    private String serializeHealth(EquipmentHealth health) {
        try {
            return objectMapper.writeValueAsString(health);
        } catch (Exception e) {
            logger.error("Failed to serialize health: {}", e.getMessage());
            return null;
        }
    }

    private String serializeAlert(CriticalAlert alert) {
        try {
            return objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            logger.error("Failed to serialize alert: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Internal record to carry both health and original telemetry through the stream.
     */
    private record EquipmentHealthWithTelemetry(EquipmentHealth health, Telemetry telemetry) {}
}
