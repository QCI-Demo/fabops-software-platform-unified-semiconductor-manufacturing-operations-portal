package com.fabops.equipment.processor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Kafka Streams configuration.
 * Sets up stream processing properties for replay-safe, durable processing.
 */
@Configuration
public class KafkaStreamsConfig {

	@Value("${kafka.streams.application-id}")
	private String applicationId;

	@Value("${kafka.streams.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${kafka.streams.replication-factor:1}")
	private int replicationFactor;

	@Value("${kafka.streams.state-dir:/tmp/kafka-streams}")
	private String stateDir;

	@Bean
	public Properties kafkaStreamsProperties() {
		Properties props = new Properties();
		
		// Core configuration
		props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
		props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		
		// Serialization defaults
		props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
		props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
		
		// Replay-safe processing: exactly-once semantics
		props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
		
		// State store configuration
		props.put(StreamsConfig.STATE_DIR_CONFIG, stateDir);
		props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, replicationFactor);
		
		// Consumer configuration for durability
		props.put(StreamsConfig.consumerPrefix("auto.offset.reset"), "earliest");
		
		// Producer configuration for reliability
		props.put(StreamsConfig.producerPrefix("acks"), "all");
		props.put(StreamsConfig.producerPrefix("enable.idempotence"), "true");
		
		// Commit interval for fault tolerance
		props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
		
		return props;
	}

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		return mapper;
	}
}
