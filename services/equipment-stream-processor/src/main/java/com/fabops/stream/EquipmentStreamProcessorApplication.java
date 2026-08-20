package com.fabops.stream;

import com.fabops.stream.config.KafkaStreamsConfig;
import com.fabops.stream.topology.EquipmentHealthTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Properties;

/**
 * Equipment Stream Processor Application.
 * 
 * A durable Kafka Streams application that:
 * - Consumes validated telemetry from fabops.telemetry.raw
 * - Computes equipment health metrics via rule engine
 * - Emits versioned equipment-status events
 * - Emits critical-alert events for critical conditions
 * 
 * Features:
 * - Replay-safe with exactly-once semantics
 * - Preserves correlation identifiers
 * - Supports schema evolution
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EquipmentStreamProcessorApplication {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentStreamProcessorApplication.class);
    
    private final Properties kafkaStreamsProperties;
    private final StreamsBuilder streamsBuilder;
    private KafkaStreams kafkaStreams;

    public EquipmentStreamProcessorApplication(
            KafkaStreamsConfig kafkaStreamsConfig,
            StreamsBuilder streamsBuilder,
            EquipmentHealthTopology equipmentHealthTopology) {
        this.kafkaStreamsProperties = kafkaStreamsConfig.kafkaStreamsProperties();
        this.streamsBuilder = streamsBuilder;
        // Topology is built via @Bean method in EquipmentHealthTopology
    }

    @Bean
    public StreamsBuilder streamsBuilder() {
        return new StreamsBuilder();
    }

    @PostConstruct
    public void start() {
        Topology topology = streamsBuilder.build();
        logger.info("Starting Kafka Streams with topology:\n{}", topology.describe());
        
        kafkaStreams = new KafkaStreams(topology, kafkaStreamsProperties);
        
        // Set up state listener for monitoring
        kafkaStreams.setStateListener((newState, oldState) -> {
            logger.info("Kafka Streams state changed from {} to {}", oldState, newState);
        });
        
        // Set up uncaught exception handler for resilience
        kafkaStreams.setUncaughtExceptionHandler(exception -> {
            logger.error("Uncaught exception in stream thread", exception);
            return KafkaStreams.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });
        
        kafkaStreams.start();
        logger.info("Equipment Stream Processor started");
    }

    @PreDestroy
    public void stop() {
        if (kafkaStreams != null) {
            logger.info("Shutting down Kafka Streams...");
            kafkaStreams.close();
            logger.info("Kafka Streams shutdown complete");
        }
    }

    /**
     * Provides access to Kafka Streams state for health checks.
     */
    @Bean
    public KafkaStreams kafkaStreams() {
        return kafkaStreams;
    }

    public static void main(String[] args) {
        SpringApplication.run(EquipmentStreamProcessorApplication.class, args);
    }
}
