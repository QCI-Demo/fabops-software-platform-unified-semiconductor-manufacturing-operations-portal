{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['event_date', 'equipment_id'],
        tags=['kpi', 'equipment_health', 'daily'],
        post_hook=[
            "{{ log_transformation_metadata(this, 'kpi_equipment_health_daily') }}"
        ]
    )
}}

/*
    KPI: Equipment Health Daily Summary
    
    Daily rollup of equipment health metrics for executive dashboards and trend analysis.
    Derived from hourly KPI model for efficiency.
    
    Key Metrics:
    - Overall Equipment Effectiveness (OEE) proxy via health score
    - Critical incident rate
    - Uptime percentage (healthy state percentage)
*/

with hourly_data as (
    select *
    from {{ ref('kpi_equipment_health_hourly') }}
    {% if is_incremental() %}
    where event_date >= (
        select coalesce(
            dateadd(day, -1, max(event_date)),
            '1970-01-01'::date
        )
        from {{ this }}
    )
    {% endif %}
),

daily_aggregates as (
    select
        equipment_id,
        event_date,
        
        -- Health score daily summary
        avg(avg_health_score) as avg_health_score,
        min(min_health_score) as min_health_score,
        max(max_health_score) as max_health_score,
        avg(stddev_health_score) as avg_hourly_stddev,
        
        -- Volume metrics
        sum(total_events) as total_events,
        count(*) as hours_with_data,
        
        -- Severity totals
        sum(healthy_count) as healthy_events,
        sum(warning_count) as warning_events,
        sum(critical_count) as critical_events,
        sum(is_critical_count) as critical_flag_events,
        
        -- Dimension breakdown totals
        sum(temperature_critical_count) as temp_critical_events,
        sum(temperature_warning_count) as temp_warning_events,
        sum(pressure_critical_count) as pressure_critical_events,
        sum(pressure_warning_count) as pressure_warning_events,
        sum(error_critical_count) as error_critical_events,
        sum(error_warning_count) as error_warning_events,
        
        -- Derived daily KPIs
        round(100.0 * sum(healthy_count) / nullif(sum(total_events), 0), 2) as uptime_pct,
        round(100.0 * sum(critical_count) / nullif(sum(total_events), 0), 2) as critical_incident_rate,
        round(100.0 * sum(warning_count) / nullif(sum(total_events), 0), 2) as warning_rate,
        
        -- OEE proxy: weighted average considering severity distribution
        -- Score = (100 * healthy + 50 * warning + 0 * critical) / total
        round(
            (100.0 * sum(healthy_count) + 50.0 * sum(warning_count)) / 
            nullif(sum(total_events), 0), 
            2
        ) as oee_proxy_score,
        
        -- Mean Time Between Failures proxy (hours between critical events)
        case 
            when sum(critical_count) > 0 
            then round(1.0 * count(*) / sum(critical_count), 2)
            else null 
        end as mtbf_hours_proxy,
        
        -- Timestamps
        min(first_event_at) as first_event_at,
        max(last_event_at) as last_event_at
        
    from hourly_data
    group by 1, 2
)

select
    {{ dbt_utils.generate_surrogate_key(['equipment_id', 'event_date']) }} as surrogate_key,
    *,
    -- Transformation metadata
    current_timestamp() as dbt_updated_at,
    '{{ invocation_id }}' as dbt_invocation_id,
    '{{ this.schema }}.{{ this.identifier }}' as dbt_model_name
from daily_aggregates
