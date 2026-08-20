package com.fabops.streaming;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Application context test for Equipment Status Processor.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "kafka.streams.bootstrap-servers=localhost:9092",
    "kafka.schema-registry.url=http://localhost:8081",
    "spring.main.allow-bean-definition-overriding=true"
})
class EquipmentStatusProcessorApplicationTests {

    @Test
    void contextLoads() {
        // Verify application context loads correctly
    }
}
