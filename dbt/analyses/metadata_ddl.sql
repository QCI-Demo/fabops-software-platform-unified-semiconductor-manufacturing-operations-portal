/*
    DDL for metadata and lineage tracking tables.
    Run this script manually before first dbt run if lineage tracking is enabled.
*/

-- Create metadata schema if not exists
CREATE SCHEMA IF NOT EXISTS {{ database }}.METADATA;

-- Transformation lineage tracking table
CREATE TABLE IF NOT EXISTS {{ database }}.METADATA.TRANSFORMATION_LINEAGE (
    invocation_id VARCHAR(36) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    full_table_name VARCHAR(512),
    target_schema VARCHAR(255),
    run_started_at TIMESTAMP_NTZ,
    completed_at TIMESTAMP_NTZ,
    dbt_command VARCHAR(50),
    target_environment VARCHAR(50),
    upstream_models VARIANT,
    run_id VARCHAR(100),
    job_id VARCHAR(100),
    PRIMARY KEY (invocation_id, model_name)
);

-- Model refresh history for incremental tracking
CREATE TABLE IF NOT EXISTS {{ database }}.METADATA.MODEL_REFRESH_HISTORY (
    model_name VARCHAR(255) NOT NULL,
    refresh_timestamp TIMESTAMP_NTZ NOT NULL,
    row_count BIGINT,
    bytes_scanned BIGINT,
    is_full_refresh BOOLEAN,
    invocation_id VARCHAR(36),
    PRIMARY KEY (model_name, refresh_timestamp)
);

COMMENT ON TABLE {{ database }}.METADATA.TRANSFORMATION_LINEAGE IS 
    'Tracks dbt model transformations for data lineage and audit purposes';

COMMENT ON TABLE {{ database }}.METADATA.MODEL_REFRESH_HISTORY IS 
    'Tracks incremental model refresh statistics';
