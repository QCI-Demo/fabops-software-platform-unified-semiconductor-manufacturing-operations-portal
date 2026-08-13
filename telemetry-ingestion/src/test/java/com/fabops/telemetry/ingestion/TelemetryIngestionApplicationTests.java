package com.fabops.telemetry.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class TelemetryIngestionApplicationTests {

	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@MockitoBean
	private io.confluent.kafka.schemaregistry.client.SchemaRegistryClient schemaRegistryClient;

	@Test
	void contextLoads() {
	}

}
