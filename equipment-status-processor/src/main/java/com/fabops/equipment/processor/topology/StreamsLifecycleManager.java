package com.fabops.equipment.processor.topology;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;

/**
 * Manages the Kafka Streams lifecycle.
 * Handles startup, shutdown, and error handling for the stream processor.
 */
@Component
public class StreamsLifecycleManager {

	private static final Logger log = LoggerFactory.getLogger(StreamsLifecycleManager.class);

	private final Properties kafkaStreamsProperties;
	private final EquipmentHealthTopology equipmentHealthTopology;
	private KafkaStreams streams;

	public StreamsLifecycleManager(
			Properties kafkaStreamsProperties,
			EquipmentHealthTopology equipmentHealthTopology
	) {
		this.kafkaStreamsProperties = kafkaStreamsProperties;
		this.equipmentHealthTopology = equipmentHealthTopology;
	}

	@PostConstruct
	public void start() {
		// Build topology
		StreamsBuilder builder = new StreamsBuilder();
		equipmentHealthTopology.buildTopology(builder);
		Topology topology = builder.build();

		log.info("Kafka Streams topology:\n{}", topology.describe());

		// Create and configure streams
		streams = new KafkaStreams(topology, kafkaStreamsProperties);

		// Set up error handler for uncaught exceptions
		streams.setUncaughtExceptionHandler(exception -> {
			log.error("Uncaught exception in Kafka Streams: {}", exception.getMessage(), exception);
			// Replace the failed thread - allows recovery from transient errors
			return KafkaStreams.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
		});

		// Set up state listener for monitoring
		streams.setStateListener((newState, oldState) -> {
			log.info("Kafka Streams state changed: {} -> {}", oldState, newState);
			if (newState == KafkaStreams.State.ERROR) {
				log.error("Kafka Streams entered ERROR state");
			}
		});

		// Start the streams
		streams.start();
		log.info("Kafka Streams started");
	}

	@PreDestroy
	public void stop() {
		if (streams != null) {
			log.info("Shutting down Kafka Streams...");
			streams.close(Duration.ofSeconds(30));
			log.info("Kafka Streams shutdown complete");
		}
	}

	/**
	 * Returns the current state of the Kafka Streams application.
	 */
	public KafkaStreams.State getState() {
		return streams != null ? streams.state() : null;
	}

	/**
	 * Checks if the streams application is running.
	 */
	public boolean isRunning() {
		return streams != null && streams.state().isRunningOrRebalancing();
	}
}
