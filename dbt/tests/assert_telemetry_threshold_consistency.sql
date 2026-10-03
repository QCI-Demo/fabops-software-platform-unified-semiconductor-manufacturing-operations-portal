-- Test: Verify that telemetry threshold classifications are consistent
-- Temperature and pressure status should match threshold definitions

with telemetry_checks as (
    select
        surrogate_key,
        avg_temperature,
        max_temperature,
        temperature_critical_count,
        temperature_warning_count,
        calc_avg_health_score
    from {{ ref('kpi_telemetry_hourly') }}
    where max_temperature is not null
),

inconsistent_classifications as (
    select *
    from telemetry_checks
    where 
        -- If max temp >= 90, should have critical counts
        (max_temperature >= 90 and temperature_critical_count = 0)
        -- If no readings exceed warning threshold, should have no warnings/criticals
        or (max_temperature < 75 and (temperature_warning_count > 0 or temperature_critical_count > 0))
)

select * from inconsistent_classifications
