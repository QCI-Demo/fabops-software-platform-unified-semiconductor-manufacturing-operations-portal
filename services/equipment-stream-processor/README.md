# Equipment Stream Processor

A durable Kafka Streams application that consumes validated telemetry, computes equipment health metrics, and emits versioned equipment-status and critical-alert events.

## Features

- **Replay-safe**: Uses exactly-once processing semantics
- **Correlation tracking**: Preserves correlation identifiers throughout the pipeline
- **Schema evolution**: Versioned event records with backward compatibility
- **Health rule engine**: Configurable thresholds for temperature, pressure, and error codes
- **Critical alerting**: Automatic detection and alerting for critical conditions

## Architecture

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │              Equipment Stream Processor                      │
                    │                                                              │
┌──────────────┐    │   ┌───────────┐    ┌─────────────┐    ┌──────────────────┐  │    ┌─────────────────┐
│  telemetry   │────┼──▶│  Source   │───▶│ Health Rule │───▶│ Equipment Status │──┼───▶│ equipment.status│
│    .raw      │    │   │  Stream   │    │   Engine    │    │     Branch       │  │    │   (compacted)   │
└──────────────┘    │   └───────────┘    └─────────────┘    └──────────────────┘  │    └─────────────────┘
                    │                           │                                  │
                    │                           ▼                                  │    ┌─────────────────┐
                    │                    ┌─────────────┐                          ├───▶│ alerts.critical │
                    │                    │  Critical   │──────────────────────────┘    │  (30d retention)│
                    │                    │   Branch    │                               └─────────────────┘
                    │                    └─────────────┘                              
                    └─────────────────────────────────────────────────────────────┘
```

## Topics

| Topic | Purpose | Retention/Policy |
|-------|---------|------------------|
| `fabops.telemetry.raw` | Raw telemetry from equipment sensors | 7 days retention |
| `fabops.equipment.status` | Latest equipment health status | Compacted |
| `fabops.alerts.critical` | Critical alert events | 30 days retention |

## Health Scoring

The health rule engine evaluates three dimensions:

1. **Temperature** (30% weight)
   - Warning: ≥75°C
   - Critical: ≥90°C

2. **Pressure** (30% weight)
   - Warning: <0.8 or >1.2 bar
   - Critical: <0.5 or >1.5 bar

3. **Error Codes** (40% weight)
   - Critical codes: E001, E002, E003, E010, E020
   - Warning codes: W001, W002, W003, W010

## Building

```bash
cd services/equipment-stream-processor
mvn clean package
```

## Docker

```bash
docker build -t fabops/equipment-stream-processor:1.0.0 .
```

## Kubernetes Deployment

```bash
helm install equipment-stream-processor ./helm/equipment-stream-processor \
  --set kafka.bootstrapServers=kafka:9092
```

## Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `KAFKA_REPLICATION_FACTOR` | Topic replication factor | `3` |
| `KAFKA_STREAM_THREADS` | Number of stream threads | `2` |
| `TOPIC_TELEMETRY_RAW` | Input topic name | `fabops.telemetry.raw` |
| `TOPIC_EQUIPMENT_STATUS` | Status output topic | `fabops.equipment.status` |
| `TOPIC_CRITICAL_ALERTS` | Alert output topic | `fabops.alerts.critical` |

## Monitoring

The application exposes metrics via Spring Boot Actuator:

- Health: `GET /actuator/health`
- Metrics: `GET /actuator/metrics`
- Prometheus: `GET /actuator/prometheus`
