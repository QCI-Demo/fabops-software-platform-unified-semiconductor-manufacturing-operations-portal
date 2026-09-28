-- Snowflake staging table DDL for telemetry events backfill
-- This table receives historical data from the Spark backfill job

USE ROLE SYSADMIN;
USE DATABASE FABOPS_DW;

-- Create staging schema if not exists
CREATE SCHEMA IF NOT EXISTS STAGING;

USE SCHEMA STAGING;

-- Main staging table for telemetry events
CREATE TABLE IF NOT EXISTS TELEMETRY_EVENTS (
    -- Business keys
    correlation_id VARCHAR(100) NOT NULL,
    equipment_id VARCHAR(100) NOT NULL,
    equipment_type VARCHAR(50),
    
    -- Event data
    event_timestamp TIMESTAMP_NTZ NOT NULL,
    temperature FLOAT,
    pressure FLOAT,
    error_codes ARRAY,
    payload_json VARIANT,
    
    -- Schema metadata
    schema_version VARCHAR(20),
    source_system VARCHAR(100),
    metric_name VARCHAR(100),
    unit VARCHAR(50),
    
    -- Kafka metadata (for replay tracking)
    kafka_partition INTEGER,
    kafka_offset BIGINT,
    kafka_ingest_timestamp TIMESTAMP_NTZ,
    
    -- Backfill metadata
    backfill_timestamp TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP(),
    backfill_batch_id VARCHAR(100),
    source_topic VARCHAR(200),
    
    -- Constraints
    CONSTRAINT pk_telemetry_events PRIMARY KEY (correlation_id)
);

-- Clustering for common query patterns
ALTER TABLE TELEMETRY_EVENTS CLUSTER BY (event_timestamp, equipment_id);

-- Grant permissions to backfill role
CREATE ROLE IF NOT EXISTS BACKFILL_ROLE;
GRANT USAGE ON DATABASE FABOPS_DW TO ROLE BACKFILL_ROLE;
GRANT USAGE ON SCHEMA STAGING TO ROLE BACKFILL_ROLE;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE TELEMETRY_EVENTS TO ROLE BACKFILL_ROLE;
GRANT CREATE TABLE ON SCHEMA STAGING TO ROLE BACKFILL_ROLE;

-- Create warehouse for backfill workloads
CREATE WAREHOUSE IF NOT EXISTS BACKFILL_WH
    WITH WAREHOUSE_SIZE = 'MEDIUM'
    AUTO_SUSPEND = 60
    AUTO_RESUME = TRUE
    INITIALLY_SUSPENDED = TRUE;

GRANT USAGE ON WAREHOUSE BACKFILL_WH TO ROLE BACKFILL_ROLE;

-- Index for common lookups
CREATE OR REPLACE INDEX idx_telemetry_equipment_time 
    ON TELEMETRY_EVENTS (equipment_id, event_timestamp);

COMMENT ON TABLE TELEMETRY_EVENTS IS 
'Staging table for historical telemetry events loaded via Spark backfill job. 
Supports idempotent upserts using MERGE on correlation_id.';
