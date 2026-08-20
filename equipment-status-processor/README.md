# Equipment Status Processor

A Kafka Streams application that consumes validated telemetry events, computes equipment health metrics, and emits versioned equipment-status and critical-alert events.

## Features

- **Durable Stream Processing**: Exactly-once semantics with replay-safe idempotent processing
- **Health Rule Engine**: Configurable threshold-based health scoring for temperature, pressure, vibration, humidity, and error codes
- **Critical Alert Detection**: Automatic alert generation when health score drops below threshold or critical violations occur
- **Schema Evolution**: Versioned event contracts compatible with Avro schema registry
- **Correlation Tracking**: Preserves correlation identifiers for end-to-end tracing

## Architecture

```
┌─────────────────────┐     ┌──────────────────────────┐     ┌─────────────────────┐
│ fabops.telemetry.raw│────▶│ Equipment Status         │────▶│fabops.equipment.    │
│ (7d retention)      │     │ Processor                │     │status (compacted)   │
└─────────────────────┘     │                          │     └─────────────────────┘
                            │ - Health Rule Engine     │
                            │ - Branch by severity     │     ┌─────────────────────┐
                            │ - Correlation preserved  │────▶│fabops.alerts.       │
                            └──────────────────────────┘     │critical (30d)       │
                                                             └─────────────────────┘
```

## Topics

| Topic | Purpose | Retention |
|-------|---------|-----------|
| `fabops.telemetry.raw` | Source telemetry events | 7 days |
| `fabops.equipment.status` | Equipment health status (keyed by equipment ID) | Log compacted |
| `fabops.alerts.critical` | Critical health alerts for operators | 30 days |

## Health Rule Engine

The health rule engine evaluates telemetry against configurable thresholds:

### Temperature Thresholds
- Warning: ≥85°C (-15 points)
- Critical: ≥95°C (-40 points)

### Pressure Thresholds
- Warning Low: ≤0.8 bar (-10 points)
- Warning High: ≥1.2 bar (-10 points)
- Critical Low: ≤0.5 bar (-30 points)
- Critical High: ≥1.5 bar (-30 points)

### Error Codes
- Critical codes: E001, E002, E003, FAULT, EMERGENCY (-50 points)

### Vibration Thresholds
- Warning: ≥5.0 g (-10 points)
- Critical: ≥10.0 g (-25 points)

### Health Score
- Base score: 100
- Critical threshold: <50 points triggers alert
- Status mapping:
  - RUNNING: ≥80 points
  - MAINTENANCE: 50-79 points  
  - DOWN: <30 points or any critical violation

## Configuration

### Environment Variables

```bash
# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SCHEMA_REGISTRY_URL=http://localhost:8081
KAFKA_REPLICATION_FACTOR=3
KAFKA_NUM_STREAM_THREADS=2

# Topics (optional overrides)
FABOPS_TOPICS_RAW_TELEMETRY=fabops.telemetry.raw
FABOPS_TOPICS_EQUIPMENT_STATUS=fabops.equipment.status
FABOPS_TOPICS_CRITICAL_ALERT=fabops.alerts.critical
```

### application.yml

See `src/main/resources/application.yml` for full configuration options including health thresholds.

## Building

```bash
# Build with Maven
./mvnw clean package

# Build Docker image
docker build -t fabops/equipment-status-processor:latest .
```

## Running

### Local Development

```bash
# Start Kafka and Schema Registry first
docker-compose up -d kafka schema-registry

# Run the application
./mvnw spring-boot:run
```

### Kubernetes Deployment

```bash
# Install with Helm
helm install equipment-status-processor ./chart \
  --set kafka.bootstrapServers=kafka-bootstrap:9092 \
  --set kafka.schemaRegistryUrl=http://schema-registry:8081 \
  --set replicaCount=3
```

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/info` | Application info |

## Testing

```bash
# Run unit tests
./mvnw test

# Run with test coverage
./mvnw test jacoco:report
```

## Event Schemas

### Input: RawTelemetryEvent

```json
{
  "equipmentId": "EQUIP-001",
  "timestamp": 1704067200000,
  "payload": {
    "temperature": "85.5",
    "pressure": "1.1",
    "vibration": "3.2"
  },
  "correlationId": "trace-abc123",
  "schemaVersion": "1.0.0",
  "sourceSystem": "telemetry-ingestion"
}
```

### Output: EquipmentHealth (to equipment-status topic)

```json
{
  "equipmentId": "EQUIP-001",
  "timestamp": 1704067200000,
  "healthScore": 85,
  "isCritical": false,
  "status": "RUNNING",
  "violations": [
    {
      "rule": "TEMPERATURE_WARNING",
      "severity": "WARNING",
      "metric": "temperature",
      "threshold": "85.0",
      "observedValue": "85.5"
    }
  ],
  "correlationId": "trace-abc123",
  "schemaVersion": "1.0.0"
}
```

### Output: CriticalAlert (to critical-alert topic)

```json
{
  "equipmentId": "EQUIP-001",
  "timestamp": 1704067200000,
  "alertId": "uuid-generated",
  "severity": "CRITICAL",
  "title": "Critical Health Alert: EQUIP-001",
  "description": "Equipment EQUIP-001 health score dropped to critical level: 45",
  "threshold": "50",
  "observedValue": "45",
  "correlationId": "trace-abc123",
  "schemaVersion": "1.0.0",
  "acknowledged": false
}
```

## License

Copyright © 2024 FabOps Platform Team
