# FabOps Data Warehouse - dbt Models

This directory contains the dbt (data build tool) project for transforming staged telemetry data into curated KPI datasets for the FabOps manufacturing operations portal.

## Project Structure

```
dbt/
├── dbt_project.yml          # Project configuration
├── packages.yml             # dbt package dependencies
├── profiles.yml.example     # Example profile configuration
├── models/
│   ├── staging/             # Staging layer (views)
│   │   ├── stg_telemetry.sql
│   │   ├── stg_equipment_status.sql
│   │   ├── stg_critical_alerts.sql
│   │   └── schema.yml
│   └── kpi/                 # KPI layer (incremental tables)
│       ├── kpi_equipment_health_hourly.sql
│       ├── kpi_equipment_health_daily.sql
│       ├── kpi_critical_alerts_daily.sql
│       ├── kpi_telemetry_hourly.sql
│       ├── kpi_fleet_health_summary.sql
│       ├── kpi_equipment_current_state.sql
│       └── schema.yml
├── macros/
│   ├── generate_schema_name.sql
│   └── log_transformation_metadata.sql
└── tests/
    ├── assert_health_score_consistency.sql
    ├── assert_telemetry_threshold_consistency.sql
    └── assert_fleet_summary_consistency.sql
```

## KPI Models

| Model | Granularity | Description |
|-------|-------------|-------------|
| `kpi_equipment_health_hourly` | Equipment × Hour | Health score statistics, severity distribution by hour |
| `kpi_equipment_health_daily` | Equipment × Day | Daily summary with OEE proxy and MTBF approximation |
| `kpi_critical_alerts_daily` | Equipment × Day | Critical alert aggregations by type |
| `kpi_telemetry_hourly` | Equipment × Hour | Raw telemetry measurements with calculated scores |
| `kpi_fleet_health_summary` | Day | Fleet-wide executive summary |
| `kpi_equipment_current_state` | Equipment × Hour | Near-real-time current equipment state |

## Health Scoring Formula

All health score calculations follow the `HealthRuleEngine` specification:

```
health_score = (temperature_score × 30% + pressure_score × 30% + error_score × 40%)
```

### Temperature Thresholds
- **Normal**: < 75°C → score = 100
- **Warning**: ≥ 75°C and < 90°C → score = linear degradation
- **Critical**: ≥ 90°C → score = 0

### Pressure Thresholds
- **Normal**: 0.8 - 1.2 bar → score = 100
- **Warning**: 0.5-0.8 or 1.2-1.5 bar → score = linear degradation
- **Critical**: < 0.5 or > 1.5 bar → score = 0

### Severity Determination
- **CRITICAL**: Any dimension critical OR 2+ warnings OR health score < 40
- **WARNING**: Any dimension in warning state
- **HEALTHY**: All dimensions normal

## Incremental Processing

All KPI models use incremental materialization with:

- **Strategy**: `merge` on surrogate key
- **Lookback**: 4-hour window for late-arriving data
- **Partitioning**: By `event_date` for efficient queries

```sql
{% if is_incremental() %}
where event_timestamp > (
    select coalesce(
        dateadd(hour, -{{ var('incremental_lookback_hours', 4) }}, max(hour_end)),
        '1970-01-01'::timestamp_ntz
    )
    from {{ this }}
)
{% endif %}
```

## Setup

1. Copy `profiles.yml.example` to `~/.dbt/profiles.yml` and configure credentials

2. Install dependencies:
   ```bash
   dbt deps
   ```

3. Run models:
   ```bash
   # Full refresh (first run or schema changes)
   dbt run --full-refresh
   
   # Incremental run
   dbt run
   
   # Run specific model
   dbt run --select kpi_equipment_health_hourly
   ```

4. Run tests:
   ```bash
   dbt test
   ```

## Data Lineage

Transformation metadata is captured via post-hooks to the `METADATA.TRANSFORMATION_LINEAGE` table, tracking:

- Model invocation ID
- Upstream model dependencies
- Run timestamps
- Target environment

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `SNOWFLAKE_ACCOUNT` | Snowflake account identifier | Yes |
| `SNOWFLAKE_USER` | Snowflake username | Yes |
| `SNOWFLAKE_PASSWORD` | Snowflake password | Yes |
| `SNOWFLAKE_DATABASE` | Target database | No (defaults to `FABOPS_DEV`) |

## Related Documentation

- [Equipment Stream Processor](../services/equipment-stream-processor/README.md) - Source of health events
- [Telemetry Ingestion](../telemetry-ingestion/README.md) - Source of raw telemetry
