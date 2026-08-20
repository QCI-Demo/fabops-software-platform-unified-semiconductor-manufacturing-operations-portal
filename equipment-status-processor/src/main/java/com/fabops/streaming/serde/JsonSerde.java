package com.fabops.streaming.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON Serde for Kafka Streams using Jackson ObjectMapper.
 * Supports schema evolution by ignoring unknown properties during deserialization.
 */
public class JsonSerde<T> implements Serde<T> {

    private static final Logger logger = LoggerFactory.getLogger(JsonSerde.class);

    private final ObjectMapper objectMapper;
    private final Class<T> type;

    public JsonSerde(ObjectMapper objectMapper, Class<T> type) {
        this.objectMapper = objectMapper;
        this.type = type;
    }

    @Override
    public Serializer<T> serializer() {
        return new JsonSerializer<>(objectMapper);
    }

    @Override
    public Deserializer<T> deserializer() {
        return new JsonDeserializer<>(objectMapper, type);
    }

    private static class JsonSerializer<T> implements Serializer<T> {
        private final ObjectMapper objectMapper;

        JsonSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String topic, T data) {
            if (data == null) {
                return null;
            }
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                logger.error("Error serializing object to JSON for topic {}: {}", topic, e.getMessage());
                throw new RuntimeException("Error serializing object to JSON", e);
            }
        }
    }

    private static class JsonDeserializer<T> implements Deserializer<T> {
        private final ObjectMapper objectMapper;
        private final Class<T> type;

        JsonDeserializer(ObjectMapper objectMapper, Class<T> type) {
            this.objectMapper = objectMapper;
            this.type = type;
        }

        @Override
        public T deserialize(String topic, byte[] data) {
            if (data == null || data.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(data, type);
            } catch (Exception e) {
                logger.error("Error deserializing JSON from topic {}: {}", topic, e.getMessage());
                throw new RuntimeException("Error deserializing JSON", e);
            }
        }
    }
}
