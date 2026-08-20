# Equipment Status Stream Processor

A durable Kafka Streams application that consumes validated telemetry, computes equipment health metrics, and emits versioned equipment-status and critical-alert events.

## Features

- **Replay-safe processing**: Uses exactly-once semantics for durable stream processing
- **Health calculation**: Rule engine evaluates temperature, pressure, and error codes
- **Correlation tracking**: Preserves correlation identifiers for end-to-end tracing
- **Schema evolution**: Supports versioned event contracts
- **Sub-second alert latency**: Stream processing with minimal latency

## Architecture

```
fabops.telemetry.raw ──┬──> HealthRuleEngine ──┬──> fabops.equipment.status (compacted)
                       │                        │
                       │                        └──> fabops.alerts.critical (30d retention)
                       │
                       └──> [Exactly-Once Processing Guarantee]
```

## Topics

| Topic | Type | Retention | Description |
|-------|------|-----------|-------------|
| `fabops.telemetry.raw` | Delete | 7 days | Raw equipment telemetry input |
| `fabops.equipment.status` | Compact | - | Latest equipment health status |
| `fabops.alerts.critical` | Delete | 30 days | Critical alert events |

## Health Rule Engine

The engine evaluates telemetry against configurable thresholds:

### Temperature
- Warning: ≥75°C
- Critical: ≥85°C
- Maximum: ≥95°C (score = 0)

### Pressure
- Critical Low: <0.5
- Warning Low: <1.0
- Warning High: ≥9.0
- Critical High: ≥10.0

### Error Codes
- Critical: E001, E002, E003, E010
- Warning: W001, W002, W003

### Health Score
Weighted calculation: `0.4×temperature + 0.3×pressure + 0.3×errorCode`
- Score < 40: CRITICAL
- Score < 70: WARNING
- Score ≥ 70: HEALTHY

## Building

```bash
# Build with Maven
mvn clean package

# Build Docker image
docker build -t fabops/equipment-status-processor:1.0.0 .
```

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 | Kafka broker addresses |
| `SCHEMA_REGISTRY_URL` | http://localhost:8081 | Schema Registry URL |
| `KAFKA_TOPIC_TELEMETRY_RAW` | fabops.telemetry.raw | Input topic |
| `KAFKA_TOPIC_EQUIPMENT_STATUS` | fabops.equipment.status | Status output topic |
| `KAFKA_TOPIC_CRITICAL_ALERT` | fabops.alerts.critical | Alert output topic |
| `HEALTH_TEMP_CRITICAL` | 85.0 | Critical temperature threshold |

## Deployment

### Helm Chart

```bash
# Install with Helm
helm install equipment-status-processor ./chart \
  --set kafka.bootstrapServers=kafka:9092 \
  --set replicaCount=3

# Upgrade
helm upgrade equipment-status-processor ./chart -f custom-values.yaml
```

### Health Endpoints

- `/actuator/health` - Application health
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe
- `/actuator/prometheus` - Prometheus metrics

## Development

```bash
# Run tests
mvn test

# Run locally (requires Kafka)
mvn spring-boot:run
```

## License

Proprietary - FabOps Platform
