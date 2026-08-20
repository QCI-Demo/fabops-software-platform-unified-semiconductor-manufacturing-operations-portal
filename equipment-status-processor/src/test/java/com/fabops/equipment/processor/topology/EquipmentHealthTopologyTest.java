package com.fabops.equipment.processor.topology;

import com.fabops.equipment.processor.config.HealthThresholdProperties;
import com.fabops.equipment.processor.config.TopicProperties;
import com.fabops.equipment.processor.engine.HealthRuleEngine;
import com.fabops.equipment.processor.model.EquipmentHealth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentHealthTopologyTest {

	private TopologyTestDriver testDriver;
	private TestInputTopic<String, String> inputTopic;
	private TestOutputTopic<String, String> statusOutputTopic;
	private TestOutputTopic<String, String> alertOutputTopic;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		// Configure test properties
		Properties props = new Properties();
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-equipment-health");
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
		props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
		props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());

		// Configure topic properties
		TopicProperties topicProperties = new TopicProperties();
		topicProperties.setRawTelemetry("fabops.telemetry.raw");
		topicProperties.setEquipmentStatus("fabops.equipment.status");
		topicProperties.setCriticalAlert("fabops.equipment.critical-alert");

		// Configure health thresholds
		HealthThresholdProperties healthConfig = new HealthThresholdProperties();
		healthConfig.getThresholds().getTemperature().setWarning(75.0);
		healthConfig.getThresholds().getTemperature().setCritical(90.0);
		healthConfig.getThresholds().getErrorCodes().setCritical(List.of("E001", "E002"));
		healthConfig.getThresholds().getErrorCodes().setWarning(List.of("W001"));

		// Create object mapper
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());

		// Create rule engine and topology
		HealthRuleEngine ruleEngine = new HealthRuleEngine(healthConfig);
		EquipmentHealthTopology topology = new EquipmentHealthTopology(topicProperties, ruleEngine, objectMapper);

		// Build and create test driver
		StreamsBuilder builder = new StreamsBuilder();
		topology.buildTopology(builder);
		testDriver = new TopologyTestDriver(builder.build(), props);

		// Create test topics
		inputTopic = testDriver.createInputTopic(
				"fabops.telemetry.raw",
				Serdes.String().serializer(),
				Serdes.String().serializer()
		);
		statusOutputTopic = testDriver.createOutputTopic(
				"fabops.equipment.status",
				Serdes.String().deserializer(),
				Serdes.String().deserializer()
		);
		alertOutputTopic = testDriver.createOutputTopic(
				"fabops.equipment.critical-alert",
				Serdes.String().deserializer(),
				Serdes.String().deserializer()
		);
	}

	@AfterEach
	void tearDown() {
		if (testDriver != null) {
			testDriver.close();
		}
	}

	@Test
	void healthyTelemetry_producesStatusOnly() throws Exception {
		String telemetryJson = """
			{
				"correlationId": "corr-001",
				"equipmentId": "EQ-001",
				"equipmentType": "CVD",
				"timestamp": "2024-01-15T10:30:00Z",
				"temperature": 60.0,
				"pressure": 1.0,
				"errorCodes": []
			}
			""";

		inputTopic.pipeInput("key-1", telemetryJson);

		// Should produce to status topic
		assertFalse(statusOutputTopic.isEmpty());
		var statusRecord = statusOutputTopic.readKeyValue();
		assertEquals("EQ-001", statusRecord.key);

		EquipmentHealth health = objectMapper.readValue(statusRecord.value, EquipmentHealth.class);
		assertEquals("corr-001", health.correlationId());
		assertEquals("EQ-001", health.equipmentId());
		assertEquals(100, health.healthScore());
		assertFalse(health.isCritical());

		// Should NOT produce to alert topic (healthy)
		assertTrue(alertOutputTopic.isEmpty());
	}

	@Test
	void criticalTemperature_producesStatusAndAlert() throws Exception {
		String telemetryJson = """
			{
				"correlationId": "corr-002",
				"equipmentId": "EQ-002",
				"equipmentType": "Etcher",
				"timestamp": "2024-01-15T10:35:00Z",
				"temperature": 95.0,
				"pressure": 1.0,
				"errorCodes": []
			}
			""";

		inputTopic.pipeInput("key-2", telemetryJson);

		// Should produce to status topic
		assertFalse(statusOutputTopic.isEmpty());
		var statusRecord = statusOutputTopic.readKeyValue();
		EquipmentHealth statusHealth = objectMapper.readValue(statusRecord.value, EquipmentHealth.class);
		assertTrue(statusHealth.isCritical());

		// Should ALSO produce to alert topic
		assertFalse(alertOutputTopic.isEmpty());
		var alertRecord = alertOutputTopic.readKeyValue();
		assertEquals("EQ-002", alertRecord.key);
		EquipmentHealth alertHealth = objectMapper.readValue(alertRecord.value, EquipmentHealth.class);
		assertTrue(alertHealth.isCritical());
		assertEquals("corr-002", alertHealth.correlationId());
	}

	@Test
	void criticalErrorCode_producesAlert() throws Exception {
		String telemetryJson = """
			{
				"correlationId": "corr-003",
				"equipmentId": "EQ-003",
				"equipmentType": "Litho",
				"timestamp": "2024-01-15T10:40:00Z",
				"temperature": 60.0,
				"pressure": 1.0,
				"errorCodes": ["E001"]
			}
			""";

		inputTopic.pipeInput("key-3", telemetryJson);

		// Should produce alert
		assertFalse(alertOutputTopic.isEmpty());
		var alertRecord = alertOutputTopic.readKeyValue();
		EquipmentHealth health = objectMapper.readValue(alertRecord.value, EquipmentHealth.class);
		assertTrue(health.isCritical());
	}

	@Test
	void invalidJson_isFiltered() {
		inputTopic.pipeInput("key-4", "invalid json {{{");

		// Should not produce any output
		assertTrue(statusOutputTopic.isEmpty());
		assertTrue(alertOutputTopic.isEmpty());
	}

	@Test
	void correlationId_isPreserved() throws Exception {
		String correlationId = "unique-correlation-12345-abcde";
		String telemetryJson = String.format("""
			{
				"correlationId": "%s",
				"equipmentId": "EQ-005",
				"equipmentType": "CVD",
				"timestamp": "2024-01-15T10:45:00Z",
				"temperature": 60.0,
				"pressure": 1.0,
				"errorCodes": []
			}
			""", correlationId);

		inputTopic.pipeInput("key-5", telemetryJson);

		var statusRecord = statusOutputTopic.readKeyValue();
		EquipmentHealth health = objectMapper.readValue(statusRecord.value, EquipmentHealth.class);
		assertEquals(correlationId, health.correlationId());
	}

	@Test
	void outputKey_isEquipmentId() throws Exception {
		String telemetryJson = """
			{
				"correlationId": "corr-006",
				"equipmentId": "EQUIPMENT-ID-ABC",
				"equipmentType": "CVD",
				"timestamp": "2024-01-15T10:50:00Z",
				"temperature": 60.0,
				"pressure": 1.0,
				"errorCodes": []
			}
			""";

		inputTopic.pipeInput("original-key", telemetryJson);

		var statusRecord = statusOutputTopic.readKeyValue();
		assertEquals("EQUIPMENT-ID-ABC", statusRecord.key);
	}
}
