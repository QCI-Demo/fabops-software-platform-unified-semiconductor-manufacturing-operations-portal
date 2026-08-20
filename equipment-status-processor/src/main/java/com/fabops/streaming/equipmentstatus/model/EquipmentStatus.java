package com.fabops.streaming.equipmentstatus.model;

/**
 * Equipment operational status enum.
 * Maps to EquipmentStatus Avro enum in equipment-status.avsc.
 */
public enum EquipmentStatus {
    IDLE,
    RUNNING,
    SETUP,
    MAINTENANCE,
    DOWN,
    UNKNOWN
}
