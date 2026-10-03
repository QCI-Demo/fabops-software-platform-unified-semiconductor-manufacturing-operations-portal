{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['event_date', 'equipment_id'],
        tags=['kpi', 'telemetry'],
        post_hook=[
            "{{ log_transformation_metadata(this, 'kpi_telemetry_hourly') }}"
        ]
    )
}}

/*
    KPI: Telemetry Measurements Hourly
    
    Hourly aggregation of raw telemetry measurements.
    Used for sensor trend analysis, threshold monitoring, and predictive maintenance inputs.
    
    Formula References (from HealthRuleEngine):
    - Temperature: Warning >= 75°C, Critical >= 90°C
    - Pressure: Warning <0.8 or >1.2 bar, Critical <0.5 or >1.5 bar
    
    Health Score Components (calculated here for validation):
    - temp_score: 100 if normal, linear degradation 75-90°C, 0 if critical
    - pressure_score: 100 if normal, linear degradation in warning zone, 0 if critical
    - error_score: 100 if no errors, -25 per warning code, 0 if critical code present
*/

with telemetry_data as (
    select
        event_id,
        equipment_id,
        event_timestamp,
        date(event_timestamp) as event_date,
        temperature_celsius,
        pressure_bar,
        error_codes,
        correlation_id
    from {{ ref('stg_telemetry') }}
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

-- Calculate health dimension scores per reading
with_scores as (
    select
        *,
        -- Temperature score calculation
        case
            when temperature_celsius is null then 100
            when temperature_celsius >= {{ var('temperature_critical') }} then 0
            when temperature_celsius >= {{ var('temperature_warning') }} then
                greatest(0, 100 - (
                    (temperature_celsius - {{ var('temperature_warning') }}) / 
                    ({{ var('temperature_critical') }} - {{ var('temperature_warning') }}) * 100
                ))::int
            else 100
        end as temperature_score,
        
        -- Pressure score calculation
        case
            when pressure_bar is null then 100
            when pressure_bar <= {{ var('pressure_critical_low') }} or pressure_bar >= {{ var('pressure_critical_high') }} then 0
            when pressure_bar <= {{ var('pressure_warning_low') }} then
                greatest(0, 100 - (
                    ({{ var('pressure_warning_low') }} - pressure_bar) / 
                    ({{ var('pressure_warning_low') }} - {{ var('pressure_critical_low') }}) * 100
                ))::int
            when pressure_bar >= {{ var('pressure_warning_high') }} then
                greatest(0, 100 - (
                    (pressure_bar - {{ var('pressure_warning_high') }}) / 
                    ({{ var('pressure_critical_high') }} - {{ var('pressure_warning_high') }}) * 100
                ))::int
            else 100
        end as pressure_score,
        
        -- Temperature status
        case
            when temperature_celsius is null then 'UNKNOWN'
            when temperature_celsius >= {{ var('temperature_critical') }} then 'CRITICAL'
            when temperature_celsius >= {{ var('temperature_warning') }} then 'WARNING'
            else 'NORMAL'
        end as calc_temperature_status,
        
        -- Pressure status
        case
            when pressure_bar is null then 'UNKNOWN'
            when pressure_bar <= {{ var('pressure_critical_low') }} or pressure_bar >= {{ var('pressure_critical_high') }} then 'CRITICAL'
            when pressure_bar <= {{ var('pressure_warning_low') }} or pressure_bar >= {{ var('pressure_warning_high') }} then 'WARNING'
            else 'NORMAL'
        end as calc_pressure_status
        
    from telemetry_data
),

hourly_aggregates as (
    select
        equipment_id,
        date_trunc('hour', event_timestamp) as hour_start,
        dateadd(hour, 1, date_trunc('hour', event_timestamp)) as hour_end,
        event_date,
        
        -- Event counts
        count(*) as event_count,
        count(temperature_celsius) as temperature_readings,
        count(pressure_bar) as pressure_readings,
        count_if(array_size(error_codes) > 0) as events_with_errors,
        
        -- Temperature statistics
        avg(temperature_celsius) as avg_temperature,
        min(temperature_celsius) as min_temperature,
        max(temperature_celsius) as max_temperature,
        stddev(temperature_celsius) as stddev_temperature,
        percentile_cont(0.95) within group (order by temperature_celsius) as p95_temperature,
        
        -- Pressure statistics
        avg(pressure_bar) as avg_pressure,
        min(pressure_bar) as min_pressure,
        max(pressure_bar) as max_pressure,
        stddev(pressure_bar) as stddev_pressure,
        
        -- Score statistics
        avg(temperature_score) as avg_temperature_score,
        avg(pressure_score) as avg_pressure_score,
        
        -- Calculated health score (weighted average)
        round(
            (avg(temperature_score) * {{ var('temperature_weight') }} + 
             avg(pressure_score) * {{ var('pressure_weight') }}) / 
            ({{ var('temperature_weight') }} + {{ var('pressure_weight') }}),
            2
        ) as calc_avg_health_score,
        
        -- Status counts
        count_if(calc_temperature_status = 'CRITICAL') as temperature_critical_count,
        count_if(calc_temperature_status = 'WARNING') as temperature_warning_count,
        count_if(calc_temperature_status = 'NORMAL') as temperature_normal_count,
        count_if(calc_pressure_status = 'CRITICAL') as pressure_critical_count,
        count_if(calc_pressure_status = 'WARNING') as pressure_warning_count,
        count_if(calc_pressure_status = 'NORMAL') as pressure_normal_count,
        
        -- Threshold violations (for SLA monitoring)
        round(100.0 * count_if(calc_temperature_status = 'CRITICAL') / nullif(count(temperature_celsius), 0), 2) as temperature_critical_pct,
        round(100.0 * count_if(calc_pressure_status = 'CRITICAL') / nullif(count(pressure_bar), 0), 2) as pressure_critical_pct,
        
        -- Lineage
        min(event_timestamp) as first_event_at,
        max(event_timestamp) as last_event_at
        
    from with_scores
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
