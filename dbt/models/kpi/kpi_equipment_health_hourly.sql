{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['event_date', 'equipment_id'],
        tags=['kpi', 'equipment_health'],
        post_hook=[
            "{{ log_transformation_metadata(this, 'kpi_equipment_health_hourly') }}"
        ]
    )
}}

/*
    KPI: Equipment Health Hourly Aggregation
    
    Aggregates equipment health metrics by hour for trend analysis and dashboard display.
    
    Formula (from HealthRuleEngine):
    - health_score = (temp_score * 30 + pressure_score * 30 + error_score * 40) / 100
    - severity = CRITICAL if any dimension critical OR 2+ warnings OR score < 40
    
    Partitioning: By event_date for efficient time-range queries
    Incremental: Processes only new events since last run with lookback window
*/

with source_data as (
    select
        equipment_id,
        event_timestamp,
        health_score,
        severity,
        is_critical,
        temperature_status,
        pressure_status,
        error_status,
        event_id,
        correlation_id,
        source_event_id
    from {{ ref('stg_equipment_status') }}
    {% if is_incremental() %}
    where event_timestamp > (
        select coalesce(
            dateadd(hour, -{{ var('incremental_lookback_hours', 4) }}, max(hour_end)),
            '1970-01-01'::timestamp_ntz
        )
        from {{ this }}
    )
    {% endif %}
),

hourly_aggregates as (
    select
        equipment_id,
        date_trunc('hour', event_timestamp) as hour_start,
        dateadd(hour, 1, date_trunc('hour', event_timestamp)) as hour_end,
        date(event_timestamp) as event_date,
        
        -- Health score aggregations
        avg(health_score) as avg_health_score,
        min(health_score) as min_health_score,
        max(health_score) as max_health_score,
        stddev(health_score) as stddev_health_score,
        percentile_cont(0.5) within group (order by health_score) as median_health_score,
        percentile_cont(0.05) within group (order by health_score) as p05_health_score,
        percentile_cont(0.95) within group (order by health_score) as p95_health_score,
        
        -- Severity distribution
        count(*) as total_events,
        count_if(severity = 'HEALTHY') as healthy_count,
        count_if(severity = 'WARNING') as warning_count,
        count_if(severity = 'CRITICAL') as critical_count,
        count_if(is_critical) as is_critical_count,
        
        -- Dimension status breakdown
        count_if(temperature_status = 'CRITICAL') as temperature_critical_count,
        count_if(temperature_status = 'WARNING') as temperature_warning_count,
        count_if(pressure_status = 'CRITICAL') as pressure_critical_count,
        count_if(pressure_status = 'WARNING') as pressure_warning_count,
        count_if(error_status = 'CRITICAL') as error_critical_count,
        count_if(error_status = 'WARNING') as error_warning_count,
        
        -- Derived KPIs
        round(100.0 * count_if(severity = 'HEALTHY') / nullif(count(*), 0), 2) as healthy_pct,
        round(100.0 * count_if(is_critical) / nullif(count(*), 0), 2) as critical_pct,
        
        -- Lineage tracking
        min(event_timestamp) as first_event_at,
        max(event_timestamp) as last_event_at,
        array_agg(distinct correlation_id) within group (order by correlation_id) as correlation_ids
        
    from source_data
    group by 1, 2, 3, 4
)

select
    {{ dbt_utils.generate_surrogate_key(['equipment_id', 'hour_start']) }} as surrogate_key,
    *,
    -- Transformation metadata
    current_timestamp() as dbt_updated_at,
    '{{ invocation_id }}' as dbt_invocation_id,
    '{{ this.schema }}.{{ this.identifier }}' as dbt_model_name
from hourly_aggregates
