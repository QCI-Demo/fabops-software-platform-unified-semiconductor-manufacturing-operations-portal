"""
Instrumented Backfill Job for FabOps Telemetry Data.
Uses OpenTelemetry for observability and exports Prometheus metrics.
"""

import os
import time
import logging
from datetime import datetime, timedelta
from typing import Iterator, Optional

from pyspark.sql import SparkSession, DataFrame
from pyspark.sql.functions import col, from_json, to_timestamp, current_timestamp
from pyspark.sql.types import StructType, StructField, StringType, DoubleType, TimestampType, LongType

from otel_metrics import get_metrics, calculate_lag_seconds, SparkMetricsListener

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class BackfillConfig:
    """Configuration for the backfill job."""
    
    def __init__(self):
        self.kafka_bootstrap_servers = os.getenv(
            'KAFKA_BOOTSTRAP_SERVERS',
            'kafka.fabops.svc.cluster.local:9092'
        )
        self.schema_registry_url = os.getenv(
            'SCHEMA_REGISTRY_URL',
            'http://schema-registry.fabops.svc.cluster.local:8081'
        )
        self.snowflake_url = os.getenv('SNOWFLAKE_URL', '')
        self.snowflake_user = os.getenv('SNOWFLAKE_USER', '')
        self.snowflake_database = os.getenv('SNOWFLAKE_DATABASE', 'FABOPS')
        self.snowflake_schema = os.getenv('SNOWFLAKE_SCHEMA', 'RAW')
        self.snowflake_warehouse = os.getenv('SNOWFLAKE_WAREHOUSE', 'FABOPS_WH')
        
        # Topics to backfill
        self.topics = os.getenv(
            'BACKFILL_TOPICS',
            'fabops.telemetry.raw,fabops.equipment.status'
        ).split(',')
        
        # Backfill time range
        self.start_timestamp = os.getenv('BACKFILL_START_TIMESTAMP', '')
        self.end_timestamp = os.getenv('BACKFILL_END_TIMESTAMP', '')
        
        # Batch configuration
        self.batch_size = int(os.getenv('BACKFILL_BATCH_SIZE', '10000'))
        self.max_retries = int(os.getenv('BACKFILL_MAX_RETRIES', '3'))
        self.retry_delay_seconds = int(os.getenv('BACKFILL_RETRY_DELAY', '30'))
        
        # Metrics configuration
        self.prometheus_port = int(os.getenv('PROMETHEUS_PORT', '9464'))
        self.metrics_enabled = os.getenv('METRICS_ENABLED', 'true').lower() == 'true'


# Telemetry event schema
TELEMETRY_SCHEMA = StructType([
    StructField("equipment_id", StringType(), False),
    StructField("timestamp", TimestampType(), False),
    StructField("metric_name", StringType(), False),
    StructField("metric_value", DoubleType(), False),
    StructField("unit", StringType(), True),
    StructField("facility_id", StringType(), True),
    StructField("line_id", StringType(), True),
])


class InstrumentedBackfillJob:
    """Backfill job with OpenTelemetry instrumentation."""
    
    def __init__(self, config: BackfillConfig):
        self.config = config
        self.spark: Optional[SparkSession] = None
        self.metrics = None
        self.listener = None
        
        if config.metrics_enabled:
            self.metrics = get_metrics(
                prometheus_port=config.prometheus_port,
                service_name="fabops-backfill-job"
            )
            self.listener = SparkMetricsListener(self.metrics)
    
    def create_spark_session(self) -> SparkSession:
        """Create instrumented Spark session."""
        builder = SparkSession.builder \
            .appName("FabOps-Backfill-Job") \
            .config("spark.jars.packages", 
                    "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,"
                    "net.snowflake:spark-snowflake_2.12:2.12.0-spark_3.4") \
            .config("spark.sql.adaptive.enabled", "true") \
            .config("spark.sql.adaptive.coalescePartitions.enabled", "true") \
            .config("spark.sql.shuffle.partitions", "200") \
            .config("spark.streaming.kafka.maxRatePerPartition", "10000") \
            .config("spark.metrics.namespace", "fabops_backfill") \
            .config("spark.metrics.conf.*.sink.prometheusServlet.class",
                    "org.apache.spark.metrics.sink.PrometheusServlet") \
            .config("spark.metrics.conf.*.sink.prometheusServlet.path", "/metrics") \
            .config("spark.ui.prometheus.enabled", "true")
        
        self.spark = builder.getOrCreate()
        
        # Set log level
        self.spark.sparkContext.setLogLevel("WARN")
        
        return self.spark
    
    def read_from_kafka(self, topic: str) -> DataFrame:
        """Read data from Kafka topic with offset tracking."""
        logger.info(f"Reading from Kafka topic: {topic}")
        
        kafka_options = {
            "kafka.bootstrap.servers": self.config.kafka_bootstrap_servers,
            "subscribe": topic,
            "startingOffsets": "earliest",
            "failOnDataLoss": "false",
        }
        
        # Add time-based filtering if configured
        if self.config.start_timestamp:
            kafka_options["startingTimestamp"] = self.config.start_timestamp
        if self.config.end_timestamp:
            kafka_options["endingTimestamp"] = self.config.end_timestamp
        
        df = self.spark.read \
            .format("kafka") \
            .options(**kafka_options) \
            .load()
        
        return df
    
    def transform_telemetry(self, df: DataFrame) -> DataFrame:
        """Transform raw Kafka messages to telemetry records."""
        return df \
            .select(
                col("key").cast("string").alias("kafka_key"),
                col("value").cast("string").alias("raw_value"),
                col("topic"),
                col("partition"),
                col("offset"),
                col("timestamp").alias("kafka_timestamp"),
                col("timestampType")
            ) \
            .select(
                col("kafka_key"),
                from_json(col("raw_value"), TELEMETRY_SCHEMA).alias("data"),
                col("topic"),
                col("partition"),
                col("offset"),
                col("kafka_timestamp")
            ) \
            .select(
                col("data.equipment_id"),
                col("data.timestamp").alias("event_time"),
                col("data.metric_name"),
                col("data.metric_value"),
                col("data.unit"),
                col("data.facility_id"),
                col("data.line_id"),
                col("topic").alias("source_topic"),
                col("partition").alias("source_partition"),
                col("offset").alias("source_offset"),
                col("kafka_timestamp"),
                current_timestamp().alias("processed_at")
            )
    
    def write_to_snowflake(self, df: DataFrame, table: str):
        """Write DataFrame to Snowflake with metrics."""
        start_time = time.time()
        
        snowflake_options = {
            "sfUrl": self.config.snowflake_url,
            "sfUser": self.config.snowflake_user,
            "sfDatabase": self.config.snowflake_database,
            "sfSchema": self.config.snowflake_schema,
            "sfWarehouse": self.config.snowflake_warehouse,
            "dbtable": table,
        }
        
        df.write \
            .format("snowflake") \
            .options(**snowflake_options) \
            .mode("append") \
            .save()
        
        duration = time.time() - start_time
        
        if self.metrics:
            self.metrics.observe_snowflake_write(table, duration)
        
        logger.info(f"Wrote to Snowflake table {table} in {duration:.2f}s")
    
    def process_batch(
        self,
        df: DataFrame,
        topic: str,
        batch_num: int
    ) -> tuple[int, bool]:
        """Process a batch of records with metrics and retry logic."""
        start_time = time.time()
        success = False
        records_processed = 0
        
        for attempt in range(self.config.max_retries):
            try:
                # Transform the data
                transformed = self.transform_telemetry(df)
                
                # Count records
                records_processed = transformed.count()
                
                # Update lag metrics
                if self.metrics and records_processed > 0:
                    # Get max offset and calculate lag
                    stats = df.agg({
                        "offset": "max",
                        "timestamp": "max"
                    }).collect()[0]
                    
                    max_offset = stats[0]
                    max_timestamp = stats[1]
                    
                    if max_timestamp:
                        lag = calculate_lag_seconds(max_timestamp.timestamp())
                        self.metrics.update_lag_seconds(topic, 0, lag)
                
                # Write to Snowflake
                table = f"BACKFILL_{topic.replace('.', '_').upper()}"
                self.write_to_snowflake(transformed, table)
                
                success = True
                break
                
            except Exception as e:
                logger.error(f"Batch {batch_num} attempt {attempt + 1} failed: {e}")
                
                if self.metrics:
                    self.metrics.record_error(topic, type(e).__name__)
                    self.metrics.record_retry(topic, batch_num)
                
                if attempt < self.config.max_retries - 1:
                    time.sleep(self.config.retry_delay_seconds)
        
        duration = time.time() - start_time
        
        if self.metrics:
            status = "success" if success else "failed"
            self.metrics.batch_completed(topic, status, duration)
            
            for i in range(records_processed):
                self.metrics.record_processed(topic, 0, status)
        
        return records_processed, success
    
    def run(self):
        """Execute the backfill job."""
        logger.info("Starting instrumented backfill job")
        
        # Create Spark session
        self.create_spark_session()
        
        total_records = 0
        total_batches = 0
        failed_batches = 0
        
        job_id = f"backfill-{int(time.time())}"
        
        try:
            for topic in self.config.topics:
                logger.info(f"Processing topic: {topic}")
                
                # Read from Kafka
                df = self.read_from_kafka(topic)
                
                # Get total count for progress tracking
                total_count = df.count()
                logger.info(f"Total records to process for {topic}: {total_count}")
                
                # Process in batches
                batch_num = 0
                processed = 0
                
                while processed < total_count:
                    batch_df = df.limit(self.config.batch_size)
                    records, success = self.process_batch(batch_df, topic, batch_num)
                    
                    processed += records
                    total_records += records
                    total_batches += 1
                    batch_num += 1
                    
                    if not success:
                        failed_batches += 1
                    
                    # Update progress
                    if self.metrics and total_count > 0:
                        progress = (processed / total_count) * 100
                        self.metrics.set_job_progress(job_id, progress)
                    
                    logger.info(f"Processed batch {batch_num}: {processed}/{total_count} records")
            
            logger.info(
                f"Backfill complete. Total records: {total_records}, "
                f"Batches: {total_batches}, Failed: {failed_batches}"
            )
            
        except Exception as e:
            logger.error(f"Backfill job failed: {e}")
            if self.metrics:
                self.metrics.record_error("all", "job_failure")
            raise
        
        finally:
            if self.spark:
                self.spark.stop()
            if self.metrics:
                self.metrics.shutdown()


def main():
    """Main entry point."""
    config = BackfillConfig()
    job = InstrumentedBackfillJob(config)
    job.run()


if __name__ == "__main__":
    main()
