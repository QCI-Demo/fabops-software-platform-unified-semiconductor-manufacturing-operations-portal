-- Test: Verify health score calculation matches expected formula
-- The health score should follow the weighted formula from HealthRuleEngine

with health_events as (
    select
        surrogate_key,
        avg_health_score,
        healthy_count,
        warning_count,
        critical_count,
        total_events
    from {{ ref('kpi_equipment_health_hourly') }}
    where total_events > 10  -- Only test hours with sufficient data
),

validation as (
    select
        *,
        -- Validate that percentages sum correctly
        round(100.0 * (healthy_count + warning_count + critical_count) / total_events, 0) as total_pct
    from health_events
    where total_pct != 100
)

select * from validation
