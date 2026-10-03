{{
    config(
        materialized='incremental',
        unique_key='surrogate_key',
        incremental_strategy='merge',
        cluster_by=['alert_date', 'equipment_id'],
        tags=['kpi', 'alerts'],
        post_hook=[
            "{{ log_transformation_metadata(this, 'kpi_critical_alerts_daily') }}"
        ]
    )
}}

/*
    KPI: Critical Alerts Daily Summary
    
    Daily aggregation of critical alerts by equipment and alert type.
    Used for incident analysis, root cause identification, and alerting trend reports.
    
    Key Metrics:
    - Alert frequency by type (temperature, pressure, error code, combined)
    - Time to recovery (if health events available)
    - Alert clustering patterns
*/

with alerts_data as (
    select
        alert_id,
        equipment_id,
        alert_type,
        alert_timestamp,
        date(alert_timestamp) as alert_date,
        hour(alert_timestamp) as alert_hour,
        health_score_at_alert,
        temperature_celsius,
        pressure_bar,
        error_codes,
        correlation_id
    from {{ ref('stg_critical_alerts') }}
    {% if is_incremental() %}
    where alert_timestamp > (
        select coalesce(
            dateadd(hour, -{{ var('incremental_lookback_hours', 4) }}, max(last_alert_at)),
            '1970-01-01'::timestamp_ntz
        )
        from {{ this }}
    )
    {% endif %}
),

daily_alert_aggregates as (
    select
        equipment_id,
        alert_date,
        
        -- Overall alert counts
        count(*) as total_alerts,
        count(distinct alert_id) as unique_alerts,
        count(distinct correlation_id) as unique_incidents,
        
        -- Alerts by type
        count_if(alert_type = 'TEMPERATURE_CRITICAL') as temperature_alerts,
        count_if(alert_type = 'PRESSURE_CRITICAL') as pressure_alerts,
        count_if(alert_type = 'ERROR_CODE_CRITICAL') as error_code_alerts,
        count_if(alert_type = 'COMBINED_CRITICAL') as combined_alerts,
        
        -- Health score context
        avg(health_score_at_alert) as avg_health_at_alert,
        min(health_score_at_alert) as min_health_at_alert,
        
        -- Measurement context (when available)
        avg(temperature_celsius) as avg_temperature_at_alert,
        max(temperature_celsius) as max_temperature_at_alert,
        avg(pressure_bar) as avg_pressure_at_alert,
        min(pressure_bar) as min_pressure_at_alert,
        max(pressure_bar) as max_pressure_at_alert,
        
        -- Hourly distribution (for shift analysis)
        count_if(alert_hour between 0 and 7) as alerts_shift_1,
        count_if(alert_hour between 8 and 15) as alerts_shift_2,
        count_if(alert_hour between 16 and 23) as alerts_shift_3,
        
        -- Peak hour
        mode(alert_hour) as peak_alert_hour,
        
        -- Time between alerts (clustering indicator)
        case 
            when count(*) > 1 
            then round(
                datediff('minute', min(alert_timestamp), max(alert_timestamp)) / 
                nullif(count(*) - 1, 0), 
                2
            )
            else null 
        end as avg_minutes_between_alerts,
        
        -- First and last timestamps
        min(alert_timestamp) as first_alert_at,
        max(alert_timestamp) as last_alert_at
        
    from alerts_data
    group by 1, 2
)

select
    {{ dbt_utils.generate_surrogate_key(['equipment_id', 'alert_date']) }} as surrogate_key,
    *,
    -- Transformation metadata
    current_timestamp() as dbt_updated_at,
    '{{ invocation_id }}' as dbt_invocation_id,
    '{{ this.schema }}.{{ this.identifier }}' as dbt_model_name
from daily_alert_aggregates
