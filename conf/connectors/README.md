# Kafka Connect Snowflake Sink Connectors

This directory contains Kafka Connect connector configurations for ingesting telemetry streams into Snowflake staging tables.

## Connectors

| Connector | Source Topic | Target Table | Description |
|-----------|--------------|--------------|-------------|
| `snowflake-sink-raw-telemetry` | `fabops.telemetry.raw` | `RAW_TELEMETRY_EVENTS` | Raw equipment telemetry from fab tools |
| `snowflake-sink-equipment-status` | `fabops.equipment.status` | `EQUIPMENT_STATUS_EVENTS` | Derived equipment operational status |
| `snowflake-sink-critical-alerts` | `fabops.equipment.critical-alert` | `CRITICAL_ALERT_EVENTS` | High-priority alerts from thresholds |
| `snowflake-sink-audit-events` | `fabops.telemetry.audit` | `AUDIT_EVENTS` | Immutable audit trail for pipeline actions |
| `snowflake-sink-dead-letter` | `fabops.telemetry.dlq` | `DEAD_LETTER_EVENTS` | Failed events for remediation |

## Configuration Features

### Schema Evolution

All connectors are configured with schema evolution handling:

- **Avro Converter**: Uses Confluent's `AvroConverter` with Schema Registry integration
- **Enhanced Avro Support**: `enhanced.avro.schema.support=true` enables schema evolution
- **Schematization**: `snowflake.enable.schematization=true` auto-creates/evolves Snowflake table columns

### Field Mapping

Source Avro fields are automatically mapped to Snowflake columns:

| Avro Field | Snowflake Column | Notes |
|------------|------------------|-------|
| `equipmentId` | `EQUIPMENT_ID` | Equipment identifier |
| `timestamp` | `TIMESTAMP` | Event time (epoch ms → TIMESTAMP_NTZ) |
| `correlationId` | `CORRELATION_ID` | End-to-end trace ID |
| `schemaVersion` | `SCHEMA_VERSION` | Avro schema version |
| `payload.*` | Flattened with `_` | Nested payload fields are flattened |

### Kafka Metadata Columns

Each staging table includes Kafka metadata for idempotent replay:

| Column | Description |
|--------|-------------|
| `_KAFKA_TOPIC` | Source topic name |
| `_KAFKA_PARTITION` | Partition number |
| `_KAFKA_OFFSET` | Message offset (enables replay deduplication) |
| `_KAFKA_INGEST_TS` | Snowflake ingestion timestamp |

### Error Handling

All connectors use dead-letter queue (DLQ) routing for failed records:

- `errors.tolerance=all`: Continue processing despite failures
- DLQ topics: `fabops.connect.dlq.<connector-name>`
- Context headers enabled for debugging

## Environment Variables

The following environment variables must be set:

| Variable | Description |
|----------|-------------|
| `SNOWFLAKE_URL` | Snowflake account URL (e.g., `xy12345.snowflakecomputing.com`) |
| `SNOWFLAKE_USER` | Snowflake service account username |
| `SNOWFLAKE_PRIVATE_KEY` | RSA private key (PEM format, base64 encoded) |
| `SNOWFLAKE_PRIVATE_KEY_PASSPHRASE` | Private key passphrase (if encrypted) |
| `SCHEMA_REGISTRY_URL` | Confluent Schema Registry URL |

## Usage

### Validate Configurations

```bash
# Validate against local Connect instance
./validate-connectors.sh http://localhost:8083

# Validate against production Connect cluster
./validate-connectors.sh http://kafka-connect.fabops.svc:8083
```

### Deploy Connectors

```bash
# Dry run (show what would be deployed)
./deploy-connectors.sh http://localhost:8083 --dry-run

# Deploy to Connect cluster
./deploy-connectors.sh http://kafka-connect.fabops.svc:8083
```

### Manual Deployment via curl

```bash
# Create a new connector
curl -X POST -H "Content-Type: application/json" \
  -d @snowflake-sink-raw-telemetry.json \
  http://kafka-connect:8083/connectors

# Update existing connector config
curl -X PUT -H "Content-Type: application/json" \
  -d "$(jq '.config' snowflake-sink-raw-telemetry.json)" \
  http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/config

# Check connector status
curl http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/status

# Restart connector tasks
curl -X POST http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/restart
```

## Snowflake Target Schema

Connectors write to the `FABOPS_DW.STAGING` schema. Expected DDL:

```sql
-- Target database and schema
CREATE DATABASE IF NOT EXISTS FABOPS_DW;
CREATE SCHEMA IF NOT EXISTS FABOPS_DW.STAGING;

-- Tables are auto-created by Snowpipe Streaming with schematization enabled
-- Manual DDL example for RAW_TELEMETRY_EVENTS:
CREATE TABLE IF NOT EXISTS FABOPS_DW.STAGING.RAW_TELEMETRY_EVENTS (
    EQUIPMENT_ID VARCHAR(256),
    TIMESTAMP TIMESTAMP_NTZ,
    PAYLOAD VARIANT,
    CORRELATION_ID VARCHAR(256),
    SCHEMA_VERSION VARCHAR(32),
    SOURCE_SYSTEM VARCHAR(256),
    METRIC_NAME VARCHAR(256),
    UNIT VARCHAR(64),
    _KAFKA_TOPIC VARCHAR(256),
    _KAFKA_PARTITION INTEGER,
    _KAFKA_OFFSET INTEGER,
    _KAFKA_INGEST_TS TIMESTAMP_NTZ
);
```

## Monitoring

### Grafana Dashboards

Monitor connector health via Kafka Connect JMX metrics exposed to Prometheus:

- `kafka_connect_connector_status`: Connector running state
- `kafka_connect_task_status`: Task-level health
- `kafka_connect_sink_task_offset_commit_seq_no`: Commit progress
- `kafka_connect_task_error_count`: Error rates

### Connect REST API Health Checks

```bash
# List all connectors and their status
curl http://kafka-connect:8083/connectors?expand=status

# Get specific connector task status
curl http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/tasks/0/status
```

## Backfill / Replay

For historical data backfill, reset consumer offsets:

```bash
# Stop the connector
curl -X PUT http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/pause

# Reset offsets (requires Kafka admin tools)
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group connect-snowflake-sink-raw-telemetry \
  --topic fabops.telemetry.raw \
  --reset-offsets --to-earliest --execute

# Resume the connector
curl -X PUT http://kafka-connect:8083/connectors/snowflake-sink-raw-telemetry/resume
```

Idempotent replay is supported via the `_KAFKA_OFFSET` and `_KAFKA_PARTITION` metadata columns, enabling deduplication in downstream ELT transformations.
