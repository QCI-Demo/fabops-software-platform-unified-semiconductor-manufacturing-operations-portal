package com.fabops.telemetry.ingestion.controller;

import com.fabops.telemetry.ingestion.dto.IngestionResponse;
import com.fabops.telemetry.ingestion.exception.SchemaValidationException;
import com.fabops.telemetry.ingestion.service.TelemetryProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final TelemetryProducer telemetryProducer;

	public GlobalExceptionHandler(TelemetryProducer telemetryProducer) {
		this.telemetryProducer = telemetryProducer;
	}

	@ExceptionHandler(SchemaValidationException.class)
	public ResponseEntity<IngestionResponse> handleValidationError(SchemaValidationException ex) {
		try {
			telemetryProducer.sendToDeadLetter(
					ex.getOriginalPayload(),
					ex.getErrors(),
					ex.getCorrelationId(),
					ex.getSchemaName(),
					ex.getSchemaVersion()
			).get();
		}
		catch (Exception publishError) {
			log.error("Failed to publish dead-letter message for correlationId={}",
					ex.getCorrelationId(), publishError);
		}

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(IngestionResponse.rejected(ex.getCorrelationId(), ex.getErrors()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<IngestionResponse> handleUnexpected(Exception ex) {
		log.error("Unexpected ingestion failure", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(IngestionResponse.rejected("UNKNOWN", java.util.List.of(ex.getMessage())));
	}
}
