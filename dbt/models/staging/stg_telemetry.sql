{{
    config(
        materialized='view',
        tags=['staging', 'telemetry']
    )
}}

/*
    Staging model for raw telemetry events
    Source: Kafka Connect sink table for fabops.telemetry.raw topic
    
    Transforms raw telemetry data into a clean, typed format for downstream KPI models.
*/

with source as (
    select * from {{ source('raw', 'raw_telemetry_events') }}
),

renamed as (
    select
        -- Primary identifiers
        record_content:eventId::string as event_id,
        record_content:equipmentId::string as equipment_id,
        record_content:correlationId::string as correlation_id,
        
        -- Timestamps
        to_timestamp_ntz(record_content:timestamp::string) as event_timestamp,
        record_metadata:CreateTime::timestamp_ntz as kafka_timestamp,
        
        -- Measurements
        record_content:temperature::double as temperature_celsius,
        record_content:pressure::double as pressure_bar,
        
        -- Error codes as array
        record_content:errorCodes::array as error_codes,
        
        -- Schema versioning
        coalesce(record_content:schemaVersion::int, 1) as schema_version,
        
        -- Kafka metadata for lineage
        record_metadata:offset::bigint as kafka_offset,
        record_metadata:partition::int as kafka_partition,
        record_metadata:topic::string as kafka_topic,
        
        -- Ingestion metadata
        _ingested_at as ingested_at
        
    from source
    where record_content:eventId is not null
      and record_content:equipmentId is not null
)

select * from renamed
