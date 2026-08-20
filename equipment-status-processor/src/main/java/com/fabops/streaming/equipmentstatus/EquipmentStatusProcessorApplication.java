package com.fabops.streaming.equipmentstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Equipment Status Processor - Kafka Streams Application
 * 
 * Consumes validated telemetry events, computes equipment health metrics,
 * and emits versioned equipment-status and critical-alert events.
 * 
 * Features:
 * - Durable stream processing with exactly-once semantics
 * - Replay-safe processing (idempotent based on correlation IDs)
 * - Configurable health rule engine with threshold-based alerting
 * - Schema evolution support via versioned events
 */
@SpringBootApplication
@EnableConfigurationProperties
public class EquipmentStatusProcessorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EquipmentStatusProcessorApplication.class, args);
    }
}
