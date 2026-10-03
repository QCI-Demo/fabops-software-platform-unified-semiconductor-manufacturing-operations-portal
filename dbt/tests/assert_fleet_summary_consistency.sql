-- Test: Verify fleet summary totals match individual equipment data
-- Fleet aggregates should be the sum of equipment-level data

with equipment_totals as (
    select
        event_date,
        count(distinct equipment_id) as equipment_count,
        sum(total_events) as total_events,
        sum(healthy_events) as healthy_events,
        sum(critical_events) as critical_events
    from {{ ref('kpi_equipment_health_daily') }}
    group by 1
),

fleet_data as (
    select
        snapshot_date,
        total_equipment,
        total_events,
        total_healthy_events,
        total_critical_events
    from {{ ref('kpi_fleet_health_summary') }}
),

mismatches as (
    select
        e.event_date,
        e.equipment_count as expected_equipment,
        f.total_equipment as fleet_equipment,
        e.total_events as expected_events,
        f.total_events as fleet_events,
        e.healthy_events as expected_healthy,
        f.total_healthy_events as fleet_healthy,
        e.critical_events as expected_critical,
        f.total_critical_events as fleet_critical
    from equipment_totals e
    join fleet_data f on e.event_date = f.snapshot_date
    where e.equipment_count != f.total_equipment
       or e.total_events != f.total_events
       or e.healthy_events != f.total_healthy_events
       or e.critical_events != f.total_critical_events
)

select * from mismatches
