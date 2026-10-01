# FabOps Ingestion Pipeline Monitoring

This directory contains monitoring and alerting configuration for the FabOps ingestion pipelines, including Kafka Connect and the historical backfill job.

## Overview

The monitoring stack exposes Prometheus metrics and provides Grafana dashboards for:

- **Kafka Connect**: JMX Exporter-based metrics for connector performance, consumer lag, error rates, and JVM health
- **Backfill Job**: OpenTelemetry-instrumented Spark job metrics for processing lag, throughput, and Snowflake write performance

## Components

### 1. Kafka Connect with JMX Exporter

Located in `kafka-connect/`:

| File | Description |
|------|-------------|
| `jmx-exporter-config.yaml` | JMX exporter rules for Kafka Connect metrics |
| `connect-distributed.properties` | Connect worker configuration |
| `Dockerfile` | Container image with JMX exporter enabled |
| `chart/` | Helm chart for Kubernetes deployment |

**Key Metrics Exposed:**
- `kafka_connect_worker_*` - Worker-level metrics
- `kafka_connect_connector_task_*` - Task-level metrics
- `kafka_connect_sink_task_*` - Sink task throughput
- `kafka_consumer_fetch_*` - Consumer lag metrics
- `jvm_*` - JVM memory and GC metrics

**Ports:**
- `8083` - Connect REST API
- `9404` - Prometheus metrics endpoint

### 2. Backfill Job with OpenTelemetry

Located in `backfill/`:

| File | Description |
|------|-------------|
| `otel_metrics.py` | OpenTelemetry/Prometheus metrics instrumentation |
| `backfill_job.py` | Instrumented Spark backfill job |
| `Dockerfile` | Container image with OTel dependencies |
| `requirements.txt` | Python dependencies |

**Key Metrics Exposed:**
- `fabops_backfill_records_processed_total` - Records processed counter
- `fabops_backfill_lag_seconds` - Processing lag gauge
- `fabops_backfill_errors_total` - Error counter by type
- `fabops_backfill_batch_duration_seconds` - Batch processing histogram
- `fabops_backfill_snowflake_write_duration_seconds` - Snowflake write latency

**Port:**
- `9464` - Prometheus metrics endpoint

### 3. Grafana Dashboards

Located in `monitoring/grafana/dashboards/`:

| Dashboard | UID | Description |
|-----------|-----|-------------|
| `kafka-connect-ingestion.json` | `fabops-kafka-connect-ingestion` | Kafka Connect performance overview |
| `backfill-job.json` | `fabops-backfill-job` | Backfill job progress and metrics |

**Dashboard Features:**
- Variable-based filtering by connector, topic, partition
- Threshold lines for alerting visualization
- Lag and error rate trend analysis
- DLQ message tracking
- JVM resource monitoring

### 4. Prometheus Alert Rules

Located in `monitoring/prometheus/ingestion-alerts.yaml`:

**Alert Categories:**

| Category | Alerts |
|----------|--------|
| Lag | `KafkaConnectHighConsumerLag` (>2min), `KafkaConnectCriticalConsumerLag` (>5min), `BackfillJobHighLag`, `BackfillJobCriticalLag` |
| Errors | `KafkaConnectHighErrorRate` (>1%), `KafkaConnectCriticalErrorRate` (>5%), `BackfillJobHighErrorRate`, `BackfillJobCriticalErrorRate` |
| Availability | `KafkaConnectorDown`, `KafkaConnectTaskFailed`, `BackfillJobStalled` |
| Resources | `KafkaConnectHighMemoryUsage`, `KafkaConnectCriticalMemoryUsage`, `KafkaConnectHighGCPressure` |
| Throughput | `KafkaConnectLowThroughput`, `BackfillHighSnowflakeWriteLatency` |

## Deployment

### Prerequisites

- Kubernetes cluster with Prometheus Operator
- Grafana with provisioning enabled
- Helm 3.x

### Deploy Kafka Connect

```bash
# Build and push Docker image
docker build -t fabops/kafka-connect:7.5.0 infrastructure/kafka-connect/
docker push fabops/kafka-connect:7.5.0

# Deploy with Helm
helm upgrade --install kafka-connect infrastructure/kafka-connect/chart \
  --namespace fabops \
  --set kafka.bootstrapServers=kafka:9092 \
  --set schemaRegistry.url=http://schema-registry:8081
```

### Deploy Backfill Job

```bash
# Build and push Docker image
docker build -t fabops/backfill-job:1.0.0 infrastructure/backfill/
docker push fabops/backfill-job:1.0.0

# Run as Kubernetes Job
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: fabops-backfill
  namespace: fabops
spec:
  template:
    spec:
      containers:
      - name: backfill
        image: fabops/backfill-job:1.0.0
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        - name: BACKFILL_TOPICS
          value: "fabops.telemetry.raw"
        ports:
        - containerPort: 9464
          name: metrics
      restartPolicy: OnFailure
EOF
```

### Configure Prometheus

Add the alert rules to Prometheus:

```bash
kubectl create configmap prometheus-rules \
  --from-file=infrastructure/monitoring/prometheus/ingestion-alerts.yaml \
  --namespace monitoring
```

Or apply as PrometheusRule CRD:

```bash
kubectl apply -f - <<EOF
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: fabops-ingestion-alerts
  namespace: monitoring
  labels:
    release: prometheus
spec:
  groups:
$(cat infrastructure/monitoring/prometheus/ingestion-alerts.yaml | sed 's/^/    /')
EOF
```

### Provision Grafana Dashboards

Copy dashboards to Grafana provisioning directory:

```bash
cp infrastructure/monitoring/grafana/dashboards/*.json \
   /var/lib/grafana/dashboards/fabops-ingestion/

cp infrastructure/monitoring/grafana/provisioning/*.yaml \
   /etc/grafana/provisioning/dashboards/
```

## Alert Response

### High Consumer Lag (> 2 minutes)

1. Check Kafka Connect logs: `kubectl logs -l app=kafka-connect -n fabops`
2. Verify connector status: `curl http://kafka-connect:8083/connectors/<name>/status`
3. Check Kafka broker health
4. Consider scaling Connect workers

### High Error Rate (> 1%)

1. Check Dead Letter Queue for failed messages
2. Review connector logs for error details
3. Verify Snowflake connectivity and credentials
4. Check schema compatibility

### Connector/Task Down

1. Restart failed tasks: `curl -X POST http://kafka-connect:8083/connectors/<name>/tasks/<id>/restart`
2. Check for configuration issues
3. Verify plugin installation
4. Review worker logs

## Metrics Reference

### Kafka Connect Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `kafka_connect_worker_connector_count` | Gauge | Number of active connectors |
| `kafka_connect_sink_task_sink_record_send_total` | Counter | Records sent to sink |
| `kafka_connect_task_error_total_errors_logged_total` | Counter | Total errors logged |
| `kafka_consumer_fetch_records_lag_max` | Gauge | Max consumer lag |

### Backfill Job Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `fabops_backfill_records_processed_total` | Counter | Total records processed |
| `fabops_backfill_lag_seconds` | Gauge | Processing lag in seconds |
| `fabops_backfill_errors_total` | Counter | Errors by type |
| `fabops_backfill_job_progress_percent` | Gauge | Job completion percentage |
| `fabops_backfill_snowflake_write_duration_seconds` | Histogram | Snowflake write latency |
