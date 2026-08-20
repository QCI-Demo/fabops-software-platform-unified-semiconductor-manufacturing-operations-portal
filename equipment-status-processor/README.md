# Equipment Status Processor

A Kafka Streams application that consumes validated telemetry events, computes equipment health metrics, and emits versioned equipment-status and critical-alert events.

## Features

- **Replay-safe processing**: Uses exactly-once semantics (EOS v2)
- **Correlation preservation**: Maintains correlation IDs from source telemetry
- **Schema evolution**: Versioned record schemas for forward/backward compatibility
- **Health rule engine**: Configurable thresholds for temperature, pressure, and error codes
- **Branching topology**: Routes to status topic (all) and critical-alert topic (severity-based)

## Topics

| Topic | Purpose | Cleanup Policy |
|-------|---------|----------------|
| `fabops.telemetry.raw` | Source telemetry | delete (7 days) |
| `fabops.equipment.status` | Equipment health status | compact |
| `fabops.equipment.critical-alert` | Critical alerts | delete (30 days) |

## Health Score Calculation

The health rule engine evaluates:

- **Temperature**: Warning at 75°C, Critical at 90°C
- **Pressure**: Warning at 0.8/1.2, Critical at 0.5/1.5
- **Error Codes**: Configurable critical/warning code lists

Score is weighted: Temperature (30%) + Pressure (30%) + Error Codes (40%)

## Configuration

Key environment variables:

```yaml
KAFKA_BOOTSTRAP_SERVERS: kafka:9092
TOPIC_RAW_TELEMETRY: fabops.telemetry.raw
TOPIC_EQUIPMENT_STATUS: fabops.equipment.status
TOPIC_CRITICAL_ALERT: fabops.equipment.critical-alert
```

## Building

```bash
# Build with Maven
mvn clean package

# Build Docker image
docker build -t fabops/equipment-status-processor .
```

## Helm Deployment

```bash
helm install equipment-status-processor ./chart \
  --set kafka.bootstrapServers=kafka:9092 \
  --set replicaCount=3
```

## Output Schema (EquipmentHealth)

```json
{
  "correlationId": "string",
  "equipmentId": "string",
  "equipmentType": "string",
  "telemetryTimestamp": "ISO-8601",
  "processedAt": "ISO-8601",
  "healthScore": 0-100,
  "isCritical": boolean,
  "severity": "HEALTHY|WARNING|CRITICAL",
  "breakdown": {
    "temperatureScore": 0-100,
    "pressureScore": 0-100,
    "errorCodeScore": 0-100,
    "violations": ["string"]
  },
  "schemaVersion": "1.0.0"
}
```
