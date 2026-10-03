{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['snapshot_date'],
        tags=['kpi', 'fleet_summary'],
        post_hook=[
            "{{ log_transformation_metadata(this, 'kpi_fleet_health_summary') }}"
        ]
    )
}}

/*
    KPI: Fleet Health Summary
    
    Daily fleet-wide summary across all equipment for executive dashboards.
    Provides aggregate view of manufacturing floor health status.
    
    Key Metrics:
    - Fleet-wide uptime percentage
    - Equipment count by health status
    - Top offenders (most critical events)
    - Overall equipment effectiveness (OEE) proxy
*/

with daily_health as (
    select *
    from {{ ref('kpi_equipment_health_daily') }}
    {% if is_incremental() %}
    where event_date >= (
        select coalesce(
            dateadd(day, -1, max(snapshot_date)),
            '1970-01-01'::date
        )
        from {{ this }}
    )
    {% endif %}
),

daily_alerts as (
    select *
    from {{ ref('kpi_critical_alerts_daily') }}
    {% if is_incremental() %}
    where alert_date >= (
        select coalesce(
            dateadd(day, -1, max(snapshot_date)),
            '1970-01-01'::date
        )
        from {{ this }}
    )
    {% endif %}
),

fleet_summary as (
    select
        h.event_date as snapshot_date,
        
        -- Fleet coverage
        count(distinct h.equipment_id) as total_equipment,
        sum(h.hours_with_data) as total_equipment_hours,
        sum(h.total_events) as total_events,
        
        -- Fleet health scores
        avg(h.avg_health_score) as fleet_avg_health_score,
        min(h.min_health_score) as fleet_min_health_score,
        percentile_cont(0.25) within group (order by h.avg_health_score) as fleet_p25_health,
        percentile_cont(0.50) within group (order by h.avg_health_score) as fleet_median_health,
        percentile_cont(0.75) within group (order by h.avg_health_score) as fleet_p75_health,
        
        -- Fleet uptime
        sum(h.healthy_events) as total_healthy_events,
        sum(h.warning_events) as total_warning_events,
        sum(h.critical_events) as total_critical_events,
        round(100.0 * sum(h.healthy_events) / nullif(sum(h.total_events), 0), 2) as fleet_uptime_pct,
        round(100.0 * sum(h.critical_events) / nullif(sum(h.total_events), 0), 2) as fleet_critical_rate,
        
        -- Fleet OEE proxy
        avg(h.oee_proxy_score) as fleet_avg_oee_proxy,
        
        -- Equipment status distribution
        count_if(h.avg_health_score >= 80) as equipment_healthy_count,
        count_if(h.avg_health_score >= 50 and h.avg_health_score < 80) as equipment_warning_count,
        count_if(h.avg_health_score < 50) as equipment_critical_count,
        
        -- Alert summary
        coalesce(sum(a.total_alerts), 0) as total_critical_alerts,
        coalesce(count(distinct a.equipment_id), 0) as equipment_with_alerts,
        
        -- Alert type breakdown
        coalesce(sum(a.temperature_alerts), 0) as total_temperature_alerts,
        coalesce(sum(a.pressure_alerts), 0) as total_pressure_alerts,
        coalesce(sum(a.error_code_alerts), 0) as total_error_code_alerts,
        coalesce(sum(a.combined_alerts), 0) as total_combined_alerts,
        
        -- Dimension breakdown fleet totals
        sum(h.temp_critical_events) as fleet_temp_critical_events,
        sum(h.pressure_critical_events) as fleet_pressure_critical_events,
        sum(h.error_critical_events) as fleet_error_critical_events,
        
        -- Lineage
        min(h.first_event_at) as first_event_at,
        max(h.last_event_at) as last_event_at
        
    from daily_health h
    left join daily_alerts a
        on h.equipment_id = a.equipment_id
        and h.event_date = a.alert_date
    group by 1
)

select
    {{ dbt_utils.generate_surrogate_key(['snapshot_date']) }} as surrogate_key,
    *,
    -- Transformation metadata
    current_timestamp() as dbt_updated_at,
    '{{ invocation_id }}' as dbt_invocation_id,
    '{{ this.schema }}.{{ this.identifier }}' as dbt_model_name
from fleet_summary
