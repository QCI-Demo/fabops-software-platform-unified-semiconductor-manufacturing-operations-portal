{{
    config(
        materialized='view',
        tags=['staging', 'equipment_status']
    )
}}

/*
    Staging model for equipment status events
    Source: Kafka Connect sink table for fabops.equipment.status topic
    
    Provides clean equipment health status data for KPI calculations.
*/

with source as (
    select * from {{ source('raw', 'equipment_status_events') }}
),

renamed as (
    select
        -- Primary identifiers
        record_content:eventId::string as event_id,
        record_content:equipmentId::string as equipment_id,
        record_content:correlationId::string as correlation_id,
        record_content:sourceEventId::string as source_event_id,
        
        -- Timestamps
        to_timestamp_ntz(record_content:timestamp::string) as event_timestamp,
        record_metadata:CreateTime::timestamp_ntz as kafka_timestamp,
        
        -- Health metrics
        record_content:healthScore::int as health_score,
        record_content:isCritical::boolean as is_critical,
        record_content:severity::string as severity,
        
        -- Status details
        record_content:temperatureStatus::string as temperature_status,
        record_content:pressureStatus::string as pressure_status,
        record_content:errorStatus::string as error_status,
        
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
