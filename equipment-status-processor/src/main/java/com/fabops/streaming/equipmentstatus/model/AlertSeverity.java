package com.fabops.streaming.equipmentstatus.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Alert severity classification.
 * Maps to AlertSeverity Avro enum in critical-alert.avsc.
 */
public enum AlertSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
