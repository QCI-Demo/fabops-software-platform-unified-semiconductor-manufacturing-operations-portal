"""
Transformer for converting raw Kafka telemetry events to Snowflake staging schema.

Handles JSON parsing, schema normalization, and data type conversions.
"""

import logging
from typing import Optional

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import (
    ArrayType,
    DoubleType,
    MapType,
    StringType,
    StructField,
    StructType,
    TimestampType,
)

from backfill.config import BackfillConfig

logger = logging.getLogger(__name__)


# Schema for raw telemetry events (matches RawTelemetryEvent Avro schema)
RAW_TELEMETRY_SCHEMA = StructType([
    StructField("equipmentId", StringType(), nullable=False),
    StructField("timestamp", StringType(), nullable=False),  # epoch millis as string
    StructField("payload", MapType(StringType(), StringType()), nullable=True),
    StructField("correlationId", StringType(), nullable=False),
    StructField("schemaVersion", StringType(), nullable=False),
    StructField("sourceSystem", StringType(), nullable=True),
    StructField("metricName", StringType(), nullable=True),
    StructField("unit", StringType(), nullable=True),
])

# Extended schema with equipment type and parsed metrics
ENRICHED_TELEMETRY_SCHEMA = StructType([
    StructField("equipmentId", StringType(), nullable=False),
    StructField("equipmentType", StringType(), nullable=True),
    StructField("timestamp", StringType(), nullable=False),
    StructField("temperature", DoubleType(), nullable=True),
    StructField("pressure", DoubleType(), nullable=True),
    StructField("errorCodes", ArrayType(StringType()), nullable=True),
    StructField("payload", MapType(StringType(), StringType()), nullable=True),
    StructField("correlationId", StringType(), nullable=False),
    StructField("schemaVersion", StringType(), nullable=False),
    StructField("sourceSystem", StringType(), nullable=True),
    StructField("metricName", StringType(), nullable=True),
    StructField("unit", StringType(), nullable=True),
])


class TelemetryTransformer:
    """
    Transforms raw Kafka telemetry events to Snowflake staging schema.
    """

    def __init__(self, spark: SparkSession, config: BackfillConfig):
        """
        Initialize the transformer.

        Args:
            spark: Active SparkSession.
            config: Backfill configuration.
        """
        self.spark = spark
        self.config = config

    def transform(self, kafka_df: DataFrame) -> DataFrame:
        """
        Transform raw Kafka DataFrame to Snowflake staging schema.

        Performs:
        1. JSON parsing of Kafka value column
        2. Schema normalization and type casting
        3. Extraction of common telemetry metrics (temperature, pressure)
        4. Timestamp conversion to Snowflake-compatible format
        5. Deduplication by correlation_id (keeps latest by event_timestamp)

        Args:
            kafka_df: Raw DataFrame from Kafka reader with 'value' column.

        Returns:
            DataFrame matching the Snowflake staging table schema.
        """
        logger.info("Starting transformation pipeline")

        # Parse JSON from Kafka value
        parsed_df = self._parse_kafka_json(kafka_df)

        # Normalize schema and extract metrics
        normalized_df = self._normalize_schema(parsed_df)

        # Convert timestamps
        timestamped_df = self._convert_timestamps(normalized_df)

        # Add ingestion metadata
        enriched_df = self._add_ingestion_metadata(timestamped_df)

        # Deduplicate by correlation_id, keeping the latest event
        deduped_df = self._deduplicate(enriched_df)

        # Select final columns for staging table
        staging_df = self._select_staging_columns(deduped_df)

        logger.info("Transformation complete")
        return staging_df

    def _parse_kafka_json(self, kafka_df: DataFrame) -> DataFrame:
        """Parse JSON from Kafka value column."""
        return kafka_df.select(
            F.col("key").cast("string").alias("kafka_key"),
            F.from_json(F.col("value").cast("string"), RAW_TELEMETRY_SCHEMA).alias("data"),
            F.col("topic").alias("kafka_topic"),
            F.col("partition").alias("kafka_partition"),
            F.col("offset").alias("kafka_offset"),
            F.col("timestamp").alias("kafka_timestamp"),
        ).select(
            "kafka_key",
            "kafka_topic",
            "kafka_partition",
            "kafka_offset",
            "kafka_timestamp",
            "data.*",
        )

    def _normalize_schema(self, df: DataFrame) -> DataFrame:
        """
        Normalize schema and extract common metrics from payload.
        """
        return df.withColumn(
            "temperature",
            F.when(
                F.col("payload").isNotNull(),
                F.col("payload").getItem("temperature").cast(DoubleType())
            ).otherwise(F.lit(None))
        ).withColumn(
            "pressure",
            F.when(
                F.col("payload").isNotNull(),
                F.col("payload").getItem("pressure").cast(DoubleType())
            ).otherwise(F.lit(None))
        ).withColumn(
            "errorCodes",
            F.when(
                F.col("payload").isNotNull() & F.col("payload").getItem("errorCodes").isNotNull(),
                F.split(F.col("payload").getItem("errorCodes"), ",")
            ).otherwise(F.array())
        ).withColumn(
            "equipmentType",
            F.coalesce(
                F.col("payload").getItem("equipmentType"),
                F.lit("UNKNOWN")
            )
        )

    def _convert_timestamps(self, df: DataFrame) -> DataFrame:
        """
        Convert timestamp fields to Snowflake-compatible format.
        """
        return df.withColumn(
            "event_timestamp",
            F.to_timestamp(F.col("timestamp").cast("long") / 1000)
        ).withColumn(
            "kafka_ingest_timestamp",
            F.to_timestamp(F.col("kafka_timestamp"))
        )

    def _add_ingestion_metadata(self, df: DataFrame) -> DataFrame:
        """
        Add metadata columns for lineage and debugging.
        """
        return df.withColumn(
            "backfill_timestamp",
            F.current_timestamp()
        ).withColumn(
            "backfill_batch_id",
            F.lit(self.config.app_name)
        ).withColumn(
            "source_topic",
            F.col("kafka_topic")
        )

    def _deduplicate(self, df: DataFrame) -> DataFrame:
        """
        Deduplicate events by correlation_id, keeping the latest by event_timestamp.

        This ensures idempotent behavior when reprocessing the same data.
        """
        from pyspark.sql.window import Window

        window_spec = Window.partitionBy("correlationId").orderBy(
            F.col("event_timestamp").desc(),
            F.col("kafka_offset").desc()
        )

        return (
            df.withColumn("row_num", F.row_number().over(window_spec))
            .filter(F.col("row_num") == 1)
            .drop("row_num")
        )

    def _select_staging_columns(self, df: DataFrame) -> DataFrame:
        """
        Select and rename columns to match Snowflake staging table schema.
        """
        return df.select(
            F.col("correlationId").alias("correlation_id"),
            F.col("equipmentId").alias("equipment_id"),
            F.col("equipmentType").alias("equipment_type"),
            F.col("event_timestamp"),
            F.col("temperature"),
            F.col("pressure"),
            F.col("errorCodes").alias("error_codes"),
            F.to_json(F.col("payload")).alias("payload_json"),
            F.col("schemaVersion").alias("schema_version"),
            F.col("sourceSystem").alias("source_system"),
            F.col("metricName").alias("metric_name"),
            F.col("unit"),
            # Metadata
            F.col("kafka_partition"),
            F.col("kafka_offset"),
            F.col("kafka_ingest_timestamp"),
            F.col("backfill_timestamp"),
            F.col("backfill_batch_id"),
            F.col("source_topic"),
        )
