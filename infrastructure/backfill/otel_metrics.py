"""
OpenTelemetry instrumentation for FabOps Backfill Spark Job.
Exports metrics to Prometheus for monitoring ingestion pipelines.
"""

import os
import time
from typing import Optional
from opentelemetry import metrics
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.exporter.prometheus import PrometheusMetricReader
from opentelemetry.sdk.resources import Resource, SERVICE_NAME, SERVICE_VERSION
from prometheus_client import start_http_server, Counter, Gauge, Histogram


class BackfillMetrics:
    """OpenTelemetry metrics for backfill job monitoring."""
    
    def __init__(
        self,
        service_name: str = "fabops-backfill-job",
        service_version: str = "1.0.0",
        prometheus_port: int = 9464
    ):
        self.service_name = service_name
        self.service_version = service_version
        self.prometheus_port = prometheus_port
        
        # Initialize OpenTelemetry
        self._init_otel()
        
        # Initialize Prometheus metrics
        self._init_prometheus_metrics()
        
        # Start Prometheus HTTP server
        start_http_server(self.prometheus_port)
    
    def _init_otel(self):
        """Initialize OpenTelemetry metrics provider."""
        resource = Resource.create({
            SERVICE_NAME: self.service_name,
            SERVICE_VERSION: self.service_version,
        })
        
        # Create Prometheus metric reader for OpenTelemetry
        prometheus_reader = PrometheusMetricReader()
        
        # Create meter provider
        self.meter_provider = MeterProvider(
            resource=resource,
            metric_readers=[prometheus_reader]
        )
        metrics.set_meter_provider(self.meter_provider)
        
        # Create meter
        self.meter = metrics.get_meter(self.service_name)
        
        # OpenTelemetry metrics
        self.otel_records_processed = self.meter.create_counter(
            name="backfill_records_processed_total",
            description="Total number of records processed by the backfill job",
            unit="1"
        )
        
        self.otel_batches_completed = self.meter.create_counter(
            name="backfill_batches_completed_total",
            description="Total number of batches completed",
            unit="1"
        )
        
        self.otel_errors = self.meter.create_counter(
            name="backfill_errors_total",
            description="Total number of errors encountered",
            unit="1"
        )
        
        self.otel_lag_seconds = self.meter.create_gauge(
            name="backfill_lag_seconds",
            description="Current processing lag in seconds",
            unit="s"
        )
    
    def _init_prometheus_metrics(self):
        """Initialize Prometheus metrics for direct export."""
        # Counters
        self.records_processed = Counter(
            'fabops_backfill_records_processed_total',
            'Total number of records processed by the backfill job',
            ['topic', 'partition', 'status']
        )
        
        self.batches_completed = Counter(
            'fabops_backfill_batches_completed_total',
            'Total number of batches completed',
            ['topic', 'status']
        )
        
        self.errors_total = Counter(
            'fabops_backfill_errors_total',
            'Total number of errors encountered',
            ['topic', 'error_type']
        )
        
        self.retries_total = Counter(
            'fabops_backfill_retries_total',
            'Total number of retry attempts',
            ['topic', 'partition']
        )
        
        # Gauges
        self.current_offset = Gauge(
            'fabops_backfill_current_offset',
            'Current offset being processed',
            ['topic', 'partition']
        )
        
        self.target_offset = Gauge(
            'fabops_backfill_target_offset',
            'Target offset to reach',
            ['topic', 'partition']
        )
        
        self.lag_records = Gauge(
            'fabops_backfill_lag_records',
            'Number of records behind target offset',
            ['topic', 'partition']
        )
        
        self.lag_seconds = Gauge(
            'fabops_backfill_lag_seconds',
            'Processing lag in seconds based on event time',
            ['topic', 'partition']
        )
        
        self.active_tasks = Gauge(
            'fabops_backfill_active_tasks',
            'Number of active Spark tasks'
        )
        
        self.job_progress_percent = Gauge(
            'fabops_backfill_job_progress_percent',
            'Overall job progress percentage',
            ['job_id']
        )
        
        self.records_per_second = Gauge(
            'fabops_backfill_records_per_second',
            'Current processing rate',
            ['topic']
        )
        
        # Histograms
        self.batch_duration_seconds = Histogram(
            'fabops_backfill_batch_duration_seconds',
            'Duration of batch processing',
            ['topic'],
            buckets=[0.1, 0.5, 1, 2, 5, 10, 30, 60, 120, 300]
        )
        
        self.record_processing_latency = Histogram(
            'fabops_backfill_record_processing_latency_seconds',
            'Latency of individual record processing',
            ['topic'],
            buckets=[0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1]
        )
        
        self.snowflake_write_duration = Histogram(
            'fabops_backfill_snowflake_write_duration_seconds',
            'Duration of Snowflake write operations',
            ['table'],
            buckets=[0.5, 1, 2, 5, 10, 30, 60, 120, 300, 600]
        )
    
    def record_processed(self, topic: str, partition: int, status: str = "success"):
        """Record a processed record."""
        self.records_processed.labels(
            topic=topic,
            partition=str(partition),
            status=status
        ).inc()
        self.otel_records_processed.add(1, {"topic": topic, "status": status})
    
    def batch_completed(self, topic: str, status: str = "success", duration: float = 0.0):
        """Record a completed batch."""
        self.batches_completed.labels(topic=topic, status=status).inc()
        self.batch_duration_seconds.labels(topic=topic).observe(duration)
        self.otel_batches_completed.add(1, {"topic": topic, "status": status})
    
    def record_error(self, topic: str, error_type: str):
        """Record an error."""
        self.errors_total.labels(topic=topic, error_type=error_type).inc()
        self.otel_errors.add(1, {"topic": topic, "error_type": error_type})
    
    def record_retry(self, topic: str, partition: int):
        """Record a retry attempt."""
        self.retries_total.labels(topic=topic, partition=str(partition)).inc()
    
    def update_offset(self, topic: str, partition: int, current: int, target: int):
        """Update offset tracking metrics."""
        self.current_offset.labels(topic=topic, partition=str(partition)).set(current)
        self.target_offset.labels(topic=topic, partition=str(partition)).set(target)
        self.lag_records.labels(topic=topic, partition=str(partition)).set(target - current)
    
    def update_lag_seconds(self, topic: str, partition: int, lag: float):
        """Update lag in seconds."""
        self.lag_seconds.labels(topic=topic, partition=str(partition)).set(lag)
    
    def set_active_tasks(self, count: int):
        """Set active task count."""
        self.active_tasks.set(count)
    
    def set_job_progress(self, job_id: str, progress: float):
        """Set job progress percentage."""
        self.job_progress_percent.labels(job_id=job_id).set(progress)
    
    def set_processing_rate(self, topic: str, rate: float):
        """Set current processing rate."""
        self.records_per_second.labels(topic=topic).set(rate)
    
    def observe_snowflake_write(self, table: str, duration: float):
        """Record Snowflake write duration."""
        self.snowflake_write_duration.labels(table=table).observe(duration)
    
    def shutdown(self):
        """Shutdown the metrics provider."""
        if hasattr(self, 'meter_provider'):
            self.meter_provider.shutdown()


class SparkMetricsListener:
    """Spark listener for collecting Spark-specific metrics."""
    
    def __init__(self, backfill_metrics: BackfillMetrics):
        self.metrics = backfill_metrics
        self.start_time = time.time()
        self.records_processed = 0
    
    def on_task_start(self, task_info: dict):
        """Called when a Spark task starts."""
        pass
    
    def on_task_end(self, task_info: dict):
        """Called when a Spark task ends."""
        records = task_info.get('records_read', 0)
        self.records_processed += records
        
        # Calculate rate
        elapsed = time.time() - self.start_time
        if elapsed > 0:
            rate = self.records_processed / elapsed
            self.metrics.set_processing_rate(
                task_info.get('topic', 'unknown'),
                rate
            )
    
    def on_job_start(self, job_info: dict):
        """Called when a Spark job starts."""
        self.start_time = time.time()
        self.records_processed = 0
    
    def on_job_end(self, job_info: dict):
        """Called when a Spark job ends."""
        job_id = job_info.get('job_id', 'unknown')
        self.metrics.set_job_progress(job_id, 100.0)


# Singleton instance
_metrics_instance: Optional[BackfillMetrics] = None


def get_metrics(
    prometheus_port: int = 9464,
    service_name: str = "fabops-backfill-job"
) -> BackfillMetrics:
    """Get or create the metrics singleton."""
    global _metrics_instance
    if _metrics_instance is None:
        _metrics_instance = BackfillMetrics(
            service_name=service_name,
            prometheus_port=prometheus_port
        )
    return _metrics_instance


def calculate_lag_seconds(event_time: float) -> float:
    """Calculate processing lag in seconds from event time."""
    return time.time() - event_time
