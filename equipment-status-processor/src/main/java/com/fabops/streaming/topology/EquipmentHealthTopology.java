package com.fabops.streaming.topology;

import com.fabops.streaming.config.KafkaStreamsConfig;
import com.fabops.streaming.engine.HealthRuleEngine;
import com.fabops.streaming.model.EquipmentHealth;
import com.fabops.streaming.model.Telemetry;
import com.fabops.streaming.serde.JsonSerde;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Streams topology for equipment health calculation.
 * 
 * Topology flow:
 * 1. Reads from fabops.telemetry.raw topic
 * 2. Maps raw telemetry to EquipmentHealth via HealthRuleEngine
 * 3. Branches based on health severity:
 *    - Critical alerts -> fabops.alerts.critical
 *    - All status updates -> fabops.equipment.status
 * 
 * Features:
 * - Replay-safe with exactly-once processing semantics
 * - Preserves correlation identifiers for tracing
 * - Supports schema evolution via versioned events
 */
@Component
public class EquipmentHealthTopology implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentHealthTopology.class);

    private static final String BRANCH_CRITICAL = "critical-alerts";
    private static final String BRANCH_STATUS = "equipment-status";

    private final Properties kafkaStreamsProperties;
    private final KafkaStreamsConfig.TopicProperties topicConfig;
    private final HealthRuleEngine healthRuleEngine;
    private final ObjectMapper objectMapper;

    private KafkaStreams streams;
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private volatile boolean running = false;

    public EquipmentHealthTopology(Properties kafkaStreamsProperties,
                                    KafkaStreamsConfig kafkaStreamsConfig,
                                    HealthRuleEngine healthRuleEngine,
                                    ObjectMapper objectMapper) {
        this.kafkaStreamsProperties = kafkaStreamsProperties;
        this.topicConfig = kafkaStreamsConfig.getTopics();
        this.healthRuleEngine = healthRuleEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Build the Kafka Streams topology.
     */
    public Topology buildTopology() {
        // 1. Instantiate StreamsBuilder
        StreamsBuilder builder = new StreamsBuilder();

        // Create Serdes for Telemetry and EquipmentHealth
        JsonSerde<Telemetry> telemetrySerde = new JsonSerde<>(objectMapper, Telemetry.class);
        JsonSerde<EquipmentHealth> healthSerde = new JsonSerde<>(objectMapper, EquipmentHealth.class);

        // 2. Read from raw telemetry topic
        KStream<String, Telemetry> sourceStream = builder.stream(
            topicConfig.getTelemetryRaw(),
            Consumed.with(Serdes.String(), telemetrySerde)
                    .withName("source-telemetry-raw")
        );

        logger.info("Building topology: reading from topic {}", topicConfig.getTelemetryRaw());

        // 3. MapValues to EquipmentHealth object
        KStream<String, EquipmentHealth> healthStream = sourceStream
            .filter((key, telemetry) -> telemetry != null && telemetry.getEquipmentId() != null,
                    Named.as("filter-valid-telemetry"))
            .mapValues(
                (readOnlyKey, telemetry) -> {
                    try {
                        return healthRuleEngine.evaluate(telemetry);
                    } catch (Exception e) {
                        logger.error("Error evaluating health for equipment {}: {}", 
                                     telemetry.getEquipmentId(), e.getMessage());
                        return null;
                    }
                },
                Named.as("map-to-equipment-health")
            )
            .filter((key, health) -> health != null, Named.as("filter-null-health"));

        // 4. Branch based on health severity
        // Split into two branches: critical alerts and equipment status
        Map<String, KStream<String, EquipmentHealth>> branches = healthStream
            .split(Named.as("health-split-"))
            .branch(
                (key, health) -> health.isCritical(),
                Branched.withConsumer(
                    criticalStream -> {
                        // Critical alerts go to alerts topic
                        criticalStream.to(
                            topicConfig.getCriticalAlert(),
                            Produced.with(Serdes.String(), healthSerde)
                                    .withName("sink-critical-alerts")
                        );
                        logger.info("Critical alerts routing to topic {}", topicConfig.getCriticalAlert());
                    },
                    BRANCH_CRITICAL
                )
            )
            .defaultBranch(
                Branched.withConsumer(
                    statusStream -> {
                        // All non-critical status updates go to status topic
                        statusStream.to(
                            topicConfig.getEquipmentStatus(),
                            Produced.with(Serdes.String(), healthSerde)
                                    .withName("sink-equipment-status")
                        );
                        logger.info("Equipment status routing to topic {}", topicConfig.getEquipmentStatus());
                    },
                    BRANCH_STATUS
                )
            );

        // Also send all health updates (including critical) to status topic for consistency
        healthStream.to(
            topicConfig.getEquipmentStatus(),
            Produced.with(Serdes.String(), healthSerde)
                    .withName("sink-all-status")
        );

        Topology topology = builder.build();
        logger.info("Topology built: {}", topology.describe());
        
        return topology;
    }

    @Override
    public void start() {
        logger.info("Starting Equipment Health Topology...");
        
        Topology topology = buildTopology();
        streams = new KafkaStreams(topology, kafkaStreamsProperties);

        // Set up error handling
        streams.setUncaughtExceptionHandler((thread, exception) -> {
            logger.error("Uncaught exception in Kafka Streams thread {}: {}", 
                        thread.getName(), exception.getMessage(), exception);
            return org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

        // Set up state listener
        streams.setStateListener((newState, oldState) -> {
            logger.info("Kafka Streams state change: {} -> {}", oldState, newState);
            if (newState == KafkaStreams.State.RUNNING) {
                startLatch.countDown();
            }
        });

        // Start the streams application
        streams.start();
        running = true;
        
        logger.info("Equipment Health Topology started");
    }

    @Override
    @PreDestroy
    public void stop() {
        if (streams != null) {
            logger.info("Stopping Equipment Health Topology...");
            streams.close(Duration.ofSeconds(30));
            running = false;
            logger.info("Equipment Health Topology stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running && streams != null && streams.state().isRunningOrRebalancing();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100; // Start late, stop early
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * Wait for streams to be running.
     */
    public boolean awaitRunning(long timeout, TimeUnit unit) throws InterruptedException {
        return startLatch.await(timeout, unit);
    }

    /**
     * Get current Kafka Streams state.
     */
    public KafkaStreams.State getState() {
        return streams != null ? streams.state() : null;
    }
}
