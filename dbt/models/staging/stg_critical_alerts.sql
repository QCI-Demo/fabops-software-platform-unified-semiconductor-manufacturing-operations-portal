{{
    config(
        materialized='view',
        tags=['staging', 'alerts']
    )
}}

/*
    Staging model for critical alert events
    Source: Kafka Connect sink table for fabops.alerts.critical topic
    
    Provides clean alert data for KPI aggregations.
*/

with source as (
    select * from {{ source('raw', 'critical_alert_events') }}
),

renamed as (
    select
        -- Primary identifiers
        record_content:alertId::string as alert_id,
        record_content:equipmentId::string as equipment_id,
        record_content:correlationId::string as correlation_id,
        record_content:sourceEventId::string as source_event_id,
        record_content:healthEventId::string as health_event_id,
        
        -- Timestamps
        to_timestamp_ntz(record_content:timestamp::string) as alert_timestamp,
        record_metadata:CreateTime::timestamp_ntz as kafka_timestamp,
        
        -- Alert details
        record_content:alertType::string as alert_type,
        record_content:message::string as alert_message,
        record_content:healthScore::int as health_score_at_alert,
        
        -- Measurement context
        record_content:temperature::double as temperature_celsius,
        record_content:pressure::double as pressure_bar,
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
    where record_content:alertId is not null
      and record_content:equipmentId is not null
)

select * from renamed
