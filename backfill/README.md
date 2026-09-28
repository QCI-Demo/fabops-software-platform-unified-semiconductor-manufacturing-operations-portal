# Historical Backfill Spark Job

PySpark batch job that reads historical telemetry data from Kafka (starting from the earliest offset), transforms to match the staging schema, and writes to Snowflake with idempotent upsert semantics using MERGE.

## Overview

This module supports the **Event-to-Warehouse Ingestion and Historical Load Pipelines** story by providing:

- **Full historical replay**: Reads from the earliest Kafka offset to capture all historical events
- **Schema transformation**: Converts raw telemetry events to Snowflake staging table format
- **Idempotent upserts**: Uses Snowflake MERGE to handle replays without duplicates
- **Event-time preservation**: Maintains original event timestamps for time-series analytics

## Architecture

```
┌─────────────────┐      ┌──────────────────┐      ┌──────────────────────┐
│  Kafka Topics   │─────▶│  Spark Backfill  │─────▶│  Snowflake Staging   │
│  (earliest)     │      │  Transform + MERGE│      │  Tables              │
└─────────────────┘      └──────────────────┘      └──────────────────────┘
```

## Usage

### Prerequisites

- Apache Spark 3.5+
- Python 3.10+
- Kafka cluster access
- Snowflake account with staging tables

### Configuration

Environment variables or `application.conf`:

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `KAFKA_TOPIC` | Source telemetry topic | `fabops.telemetry.raw` |
| `SNOWFLAKE_URL` | Snowflake account URL | - |
| `SNOWFLAKE_DATABASE` | Target database | `FABOPS_DW` |
| `SNOWFLAKE_SCHEMA` | Target schema | `STAGING` |
| `SNOWFLAKE_WAREHOUSE` | Compute warehouse | `BACKFILL_WH` |
| `BATCH_SIZE` | Records per micro-batch | `10000` |

### Running the Job

```bash
# Local development
spark-submit \
  --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,net.snowflake:spark-snowflake_2.12:2.15.0-spark_3.4 \
  src/main/python/backfill/backfill_main.py

# With custom config
spark-submit \
  --conf spark.backfill.kafka.bootstrap.servers=kafka:9092 \
  --conf spark.backfill.snowflake.url=account.snowflakecomputing.com \
  src/main/python/backfill/backfill_main.py
```

### Kubernetes / Spark Operator

```bash
kubectl apply -f chart/templates/spark-application.yaml
```

## Idempotent Upsert Logic

The job uses Snowflake's MERGE statement to ensure idempotent writes:

```sql
MERGE INTO staging.telemetry_events AS target
USING temp_batch AS source
ON target.correlation_id = source.correlation_id
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...
```

The `correlation_id` serves as the natural key for deduplication.

## Monitoring

- Job progress logged to stdout (structured JSON)
- Metrics exposed via Spark UI
- Checkpoint offsets stored in configured location for recovery

## Testing

```bash
pytest tests/ -v
```
