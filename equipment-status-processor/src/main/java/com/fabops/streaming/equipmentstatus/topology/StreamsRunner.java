package com.fabops.streaming.equipmentstatus.topology;

import com.fabops.streaming.equipmentstatus.config.KafkaStreamsConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Properties;

/**
 * Kafka Streams application runner and lifecycle management.
 */
@Configuration
public class StreamsRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamsRunner.class);
    
    private final KafkaStreamsConfig streamsConfig;
    private final StreamsBuilder streamsBuilder;
    
    private KafkaStreams kafkaStreams;
    
    @Value("${kafka.schema-registry.url:http://localhost:8081}")
    private String schemaRegistryUrl;
    
    @Autowired
    public StreamsRunner(KafkaStreamsConfig streamsConfig, StreamsBuilder streamsBuilder) {
        this.streamsConfig = streamsConfig;
        this.streamsBuilder = streamsBuilder;
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    @PostConstruct
    public void start() {
        Properties props = buildStreamsProperties();
        Topology topology = streamsBuilder.build();
        
        logger.info("Starting Kafka Streams with topology:\n{}", topology.describe());
        
        kafkaStreams = new KafkaStreams(topology, props);
        
        // Set uncaught exception handler
        kafkaStreams.setUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Uncaught exception in Kafka Streams thread {}: {}", 
                    thread.getName(), throwable.getMessage(), throwable);
            return KafkaStreams.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
        });
        
        // Set state change listener
        kafkaStreams.setStateListener((newState, oldState) -> {
            logger.info("Kafka Streams state changed: {} -> {}", oldState, newState);
        });
        
        // Start the streams application
        kafkaStreams.start();
        logger.info("Kafka Streams application started with application.id: {}", 
                streamsConfig.getApplicationId());
    }
    
    @PreDestroy
    public void stop() {
        if (kafkaStreams != null) {
            logger.info("Shutting down Kafka Streams application...");
            kafkaStreams.close(Duration.ofSeconds(30));
            logger.info("Kafka Streams application stopped");
        }
    }
    
    @Bean
    public KafkaStreams kafkaStreams() {
        return kafkaStreams;
    }
    
    private Properties buildStreamsProperties() {
        Properties props = new Properties();
        
        // Application configuration
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, streamsConfig.getApplicationId());
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, streamsConfig.getBootstrapServers());
        props.put(StreamsConfig.STATE_DIR_CONFIG, streamsConfig.getStateDir());
        
        // Processing guarantees
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, streamsConfig.getProcessingGuarantee());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, streamsConfig.getCommitIntervalMs());
        
        // Replication for internal topics
        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, streamsConfig.getReplicationFactor());
        
        // Threading
        props.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, streamsConfig.getNumStreamThreads());
        
        // Default Serdes
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        
        // Consumer config for replay safety
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Error handling - log and continue for deserialization errors
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class.getName());
        
        // Schema Registry (for Avro support if needed)
        props.put("schema.registry.url", schemaRegistryUrl);
        
        return props;
    }
}
