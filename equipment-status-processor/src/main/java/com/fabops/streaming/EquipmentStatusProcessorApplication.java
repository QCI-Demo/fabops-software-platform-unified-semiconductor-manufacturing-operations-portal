package com.fabops.streaming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Equipment Status Stream Processor Application.
 * 
 * A durable Kafka Streams application that:
 * - Consumes validated telemetry from fabops.telemetry.raw
 * - Computes equipment health metrics via rule engine
 * - Emits versioned equipment-status and critical-alert events
 * - Supports replay-safe processing with correlation identifiers
 */
@SpringBootApplication
public class EquipmentStatusProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquipmentStatusProcessorApplication.class, args);
    }
}
