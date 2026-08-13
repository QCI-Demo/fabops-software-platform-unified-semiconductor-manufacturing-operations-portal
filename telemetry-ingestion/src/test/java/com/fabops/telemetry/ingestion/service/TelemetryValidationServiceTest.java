package com.fabops.telemetry.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabops.telemetry.ingestion.config.TelemetryProperties;
import com.fabops.telemetry.ingestion.dto.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryValidationServiceTest {

	@Mock
	private SchemaLookupService schemaLookupService;

	private TelemetryValidationService validationService;

	@BeforeEach
	void setUp() throws Exception {
		TelemetryProperties properties = new TelemetryProperties(
				new TelemetryProperties.Topics("fabops.telemetry.raw", "fabops.telemetry.dlq"),
				new TelemetryProperties.SchemaRegistry("http://localhost:8081", "fabops.telemetry.raw-value", true),
				new TelemetryProperties.Ingestion("telemetry-ingestion", "RawTelemetryEvent", "1.0.0")
		);

		String schemaJson = """
				{
				  "type": "record",
				  "name": "RawTelemetryEvent",
				  "namespace": "com.fabops.telemetry.events",
				  "fields": [
				    {"name": "equipmentId", "type": "string"},
				    {"name": "timestamp", "type": {"type": "long", "logicalType": "timestamp-millis"}},
				    {"name": "payload", "type": {"type": "map", "values": "string"}},
				    {"name": "correlationId", "type": "string"},
				    {"name": "schemaVersion", "type": "string"},
				    {"name": "sourceSystem", "type": "string", "default": "unknown"},
				    {"name": "metricName", "type": ["null", "string"], "default": null},
				    {"name": "unit", "type": ["null", "string"], "default": null}
				  ]
				}
				""";
		Schema schema = new Schema.Parser().parse(schemaJson);
		when(schemaLookupService.resolveRawTelemetrySchema()).thenReturn(schema);

		validationService = new TelemetryValidationService(schemaLookupService, properties, new ObjectMapper());
	}

	@Test
	void acceptsValidPayload() {
		String json = """
				{
				  "equipmentId": "EQ-CVD-0142",
				  "timestamp": 1786437600000,
				  "payload": {"chamber_pressure_torr": "2.45"},
				  "correlationId": "corr-7f3a9c2e",
				  "schemaVersion": "1.0.0",
				  "sourceSystem": "edge-adapter-cvd",
				  "metricName": null,
				  "unit": null
				}
				""";

		ValidationResult result = validationService.validate(json);

		assertThat(result.valid()).isTrue();
		assertThat(result.errors()).isEmpty();
		assertThat(result.record().get("equipmentId").toString()).isEqualTo("EQ-CVD-0142");
	}

	@Test
	void rejectsMissingCorrelationId() {
		String json = """
				{
				  "equipmentId": "EQ-CVD-0142",
				  "timestamp": 1786437600000,
				  "payload": {"rf_power_w": "850"},
				  "schemaVersion": "1.0.0"
				}
				""";

		ValidationResult result = validationService.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).isNotEmpty();
	}

	@Test
	void rejectsWrongTimestampType() {
		String json = """
				{
				  "equipmentId": "EQ-CVD-0142",
				  "timestamp": "2026-08-11T08:40:00Z",
				  "payload": {"rf_power_w": "850"},
				  "correlationId": "corr-bad-ts",
				  "schemaVersion": "1.0.0"
				}
				""";

		ValidationResult result = validationService.validate(json);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).isNotEmpty();
	}
}
