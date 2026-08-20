package com.fabops.equipment.processor.topology;

import com.fabops.equipment.processor.config.TopicProperties;
import com.fabops.equipment.processor.engine.HealthRuleEngine;
import com.fabops.equipment.processor.model.EquipmentHealth;
import com.fabops.equipment.processor.model.EquipmentHealth.HealthSeverity;
import com.fabops.equipment.processor.model.Telemetry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka Streams topology for equipment health calculation.
 * 
 * Flow:
 * 1. Consumes from raw telemetry topic
 * 2. Deserializes and maps to Telemetry objects
 * 3. Applies health calculation via HealthRuleEngine
 * 4. Branches based on severity:
 *    - CRITICAL -> critical-alert topic
 *    - All statuses -> equipment-status topic
 * 
 * Features:
 * - Replay-safe (deterministic processing)
 * - Preserves correlation identifiers
 * - Supports schema evolution via versioned records
 */
@Component
public class EquipmentHealthTopology {

	private static final Logger log = LoggerFactory.getLogger(EquipmentHealthTopology.class);

	private final TopicProperties topicProperties;
	private final HealthRuleEngine healthRuleEngine;
	private final ObjectMapper objectMapper;

	public EquipmentHealthTopology(
			TopicProperties topicProperties,
			HealthRuleEngine healthRuleEngine,
			ObjectMapper objectMapper
	) {
		this.topicProperties = topicProperties;
		this.healthRuleEngine = healthRuleEngine;
		this.objectMapper = objectMapper;
	}

	/**
	 * Builds the Kafka Streams topology.
	 *
	 * @param builder the StreamsBuilder to configure
	 */
	public void buildTopology(StreamsBuilder builder) {
		// Step 1: Read from raw telemetry topic
		KStream<String, String> source = builder.stream(
				topicProperties.getRawTelemetry(),
				Consumed.with(Serdes.String(), Serdes.String())
						.withName("source-raw-telemetry")
		);

		// Step 2: Parse telemetry JSON and filter invalid records
		KStream<String, Telemetry> telemetryStream = source
				.mapValues((key, value) -> deserializeTelemetry(value), Named.as("parse-telemetry"))
				.filter((key, telemetry) -> telemetry != null, Named.as("filter-valid-telemetry"));

		// Step 3: Apply health calculation via rule engine
		KStream<String, EquipmentHealth> healthStream = telemetryStream
				.mapValues(
						(key, telemetry) -> healthRuleEngine.evaluate(telemetry),
						Named.as("calculate-health")
				);

		// Step 4: Re-key by equipment ID for proper partitioning
		KStream<String, EquipmentHealth> keyedHealthStream = healthStream
				.selectKey((key, health) -> health.equipmentId(), Named.as("rekey-by-equipment"));

		// Step 5: Serialize to JSON
		KStream<String, String> serializedHealthStream = keyedHealthStream
				.mapValues(
						(key, health) -> serializeHealth(health),
						Named.as("serialize-health")
				)
				.filter((key, value) -> value != null, Named.as("filter-serialization-errors"));

		// Step 6: Send ALL health events to equipment status topic
		serializedHealthStream.to(
				topicProperties.getEquipmentStatus(),
				Produced.with(Serdes.String(), Serdes.String())
						.withName("sink-equipment-status")
		);

		// Step 7: Branch critical alerts to separate topic
		keyedHealthStream
				.filter(
						(key, health) -> health.isCritical() || health.severity() == HealthSeverity.CRITICAL,
						Named.as("filter-critical-alerts")
				)
				.mapValues(
						(key, health) -> serializeHealth(health),
						Named.as("serialize-critical-alert")
				)
				.filter((key, value) -> value != null, Named.as("filter-alert-serialization-errors"))
				.to(
						topicProperties.getCriticalAlert(),
						Produced.with(Serdes.String(), Serdes.String())
								.withName("sink-critical-alert")
				);

		log.info("Equipment health topology built: {} -> [{}] -> {} / {}",
				topicProperties.getRawTelemetry(),
				"health-calculation",
				topicProperties.getEquipmentStatus(),
				topicProperties.getCriticalAlert());
	}

	/**
	 * Deserializes JSON to Telemetry object.
	 * Returns null for invalid JSON (filtered downstream).
	 */
	private Telemetry deserializeTelemetry(String json) {
		try {
			return objectMapper.readValue(json, Telemetry.class);
		} catch (JsonProcessingException e) {
			log.warn("Failed to deserialize telemetry: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Serializes EquipmentHealth to JSON.
	 * Returns null for serialization errors (filtered downstream).
	 */
	private String serializeHealth(EquipmentHealth health) {
		try {
			return objectMapper.writeValueAsString(health);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize health for equipment {}: {}",
					health.equipmentId(), e.getMessage());
			return null;
		}
	}
}
