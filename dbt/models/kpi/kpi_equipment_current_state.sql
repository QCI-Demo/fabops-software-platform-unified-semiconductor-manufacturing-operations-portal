{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['snapshot_hour', 'equipment_id'],
        tags=['kpi', 'current_state']
    )
}}

/*
    KPI: Equipment Current State
    
    Near-real-time current state of each equipment.
    Refreshes incrementally to provide latest health status for dashboards.
    
    Use Case: Real-time monitoring dashboard showing current equipment status.
*/

with latest_status as (
    select
        equipment_id,
        event_id,
        event_timestamp,
        health_score,
        severity,
        is_critical,
        temperature_status,
        pressure_status,
        error_status,
        correlation_id,
        row_number() over (
            partition by equipment_id 
            order by event_timestamp desc
        ) as rn
    from {{ ref('stg_equipment_status') }}
    {% if is_incremental() %}
    where event_timestamp > (
        select coalesce(
            dateadd(hour, -1, max(last_update)),
            '1970-01-01'::timestamp_ntz
        )
        from {{ this }}
    )
    {% endif %}
),

latest_telemetry as (
    select
        equipment_id,
        event_timestamp as telemetry_timestamp,
        temperature_celsius,
        pressure_bar,
        error_codes,
        row_number() over (
            partition by equipment_id 
            order by event_timestamp desc
        ) as rn
    from {{ ref('stg_telemetry') }}
    {% if is_incremental() %}
    where event_timestamp > (
        select coalesce(
            dateadd(hour, -1, max(last_update)),
            '1970-01-01'::timestamp_ntz
        )
        from {{ this }}
    )
    {% endif %}
),

current_state as (
    select
        s.equipment_id,
        date_trunc('hour', current_timestamp()) as snapshot_hour,
        
        -- Current health status
        s.health_score as current_health_score,
        s.severity as current_severity,
        s.is_critical as is_currently_critical,
        s.temperature_status as current_temperature_status,
        s.pressure_status as current_pressure_status,
        s.error_status as current_error_status,
        
        -- Latest telemetry values
        t.temperature_celsius as latest_temperature,
        t.pressure_bar as latest_pressure,
        t.error_codes as latest_error_codes,
        
        -- Timestamps
        s.event_timestamp as status_timestamp,
        t.telemetry_timestamp,
        greatest(s.event_timestamp, coalesce(t.telemetry_timestamp, s.event_timestamp)) as last_update,
        
        -- Staleness indicators
        datediff('minute', s.event_timestamp, current_timestamp()) as minutes_since_status,
        datediff('minute', t.telemetry_timestamp, current_timestamp()) as minutes_since_telemetry,
        
        -- Lineage
        s.correlation_id,
        s.event_id as latest_status_event_id
        
    from latest_status s
    left join latest_telemetry t
        on s.equipment_id = t.equipment_id
        and t.rn = 1
    where s.rn = 1
)

select
    {{ dbt_utils.generate_surrogate_key(['equipment_id', 'snapshot_hour']) }} as surrogate_key,
    *,
    -- Transformation metadata
    current_timestamp() as dbt_updated_at,
    '{{ invocation_id }}' as dbt_invocation_id,
    '{{ this.schema }}.{{ this.identifier }}' as dbt_model_name
from current_state
