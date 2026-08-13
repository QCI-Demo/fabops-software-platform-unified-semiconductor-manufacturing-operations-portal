# Telemetry Ingestion Service

Spring Boot REST adapter that validates FabOps telemetry payloads against Avro schemas from the schema registry, enriches accepted events with ingestion metadata, and publishes to Kafka.

## Capabilities

| Concern | Behavior |
|---------|----------|
| Ingress | `POST /api/v1/telemetry/ingest` (JSON body) |
| Validation | Avro `GenericDatumReader` against registry (classpath fallback) |
| Success path | Enrich + publish to `fabops.telemetry.raw` → HTTP `202` |
| Failure path | Publish original payload + error metadata to `fabops.telemetry.dlq` → HTTP `400` |
| Runtime | Java 21, Spring Boot, Kafka producer, Schema Registry client |

## Quick start

```bash
./mvnw spring-boot:run
```

Environment overrides:

- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`)
- `SCHEMA_REGISTRY_URL` (default `http://localhost:8081`)

### Valid request example

```bash
curl -sS -X POST http://localhost:8080/api/v1/telemetry/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "equipmentId": "EQ-CVD-0142",
    "timestamp": 1786437600000,
    "payload": {"chamber_pressure_torr": "2.45"},
    "correlationId": "corr-7f3a9c2e-1b44-4d01-9a10-88e2c1d0ab12",
    "schemaVersion": "1.0.0",
    "sourceSystem": "edge-adapter-cvd",
    "metricName": null,
    "unit": null
  }'
```

## Container

```bash
docker build -t fabops/telemetry-ingestion:0.0.1 .
docker run --rm -p 8080:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  -e SCHEMA_REGISTRY_URL=http://host.docker.internal:8081 \
  fabops/telemetry-ingestion:0.0.1
```

## Helm

Chart source: `chart/`  
Packaged artifact: `charts/telemetry-ingestion-0.1.0.tgz`

```bash
helm lint chart
helm package chart --destination charts
helm upgrade --install telemetry-ingestion charts/telemetry-ingestion-0.1.0.tgz \
  --set kafka.bootstrapServers=kafka:9092 \
  --set image.repository=fabops/telemetry-ingestion \
  --set replicaCount=2
```

Configurable values: `image.repository`, `image.tag`, `replicaCount`, `kafka.bootstrapServers`, `schemaRegistry.url`.

## CI

GitHub Actions workflow: `.github/workflows/telemetry-ingestion-ci.yml` runs Maven verify and Helm package on changes under `telemetry-ingestion/`.

## Schemas

Classpath copies of the FabOps telemetry contracts live in `src/main/resources/schemas/` (aligned with the telemetry schema registry story). At runtime the service prefers Confluent Schema Registry subject `fabops.telemetry.raw-value` and falls back to the classpath schema when the registry is unreachable.
