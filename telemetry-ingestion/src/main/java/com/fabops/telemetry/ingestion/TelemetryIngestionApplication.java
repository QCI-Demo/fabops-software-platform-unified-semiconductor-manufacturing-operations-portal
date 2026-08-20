package com.fabops.telemetry.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TelemetryIngestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelemetryIngestionApplication.class, args);
	}

}
