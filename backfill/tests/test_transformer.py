"""Tests for telemetry transformer."""

import pytest
from pyspark.sql import SparkSession
from pyspark.sql import functions as F
from pyspark.sql.types import StringType, StructField, StructType

from backfill.config import BackfillConfig
from backfill.transformer import TelemetryTransformer


@pytest.fixture(scope="module")
def spark():
    """Create a SparkSession for testing."""
    session = (
        SparkSession.builder
        .master("local[2]")
        .appName("backfill-transformer-test")
        .config("spark.sql.shuffle.partitions", "2")
        .getOrCreate()
    )
    yield session
    session.stop()


@pytest.fixture
def config():
    """Create test configuration."""
    return BackfillConfig(
        app_name="test-backfill",
        kafka_topic="test.telemetry",
    )


@pytest.fixture
def transformer(spark, config):
    """Create transformer instance."""
    return TelemetryTransformer(spark, config)


class TestTelemetryTransformer:
    """Test cases for TelemetryTransformer."""

    def test_parse_kafka_json(self, spark, transformer):
        """Test JSON parsing from Kafka value column."""
        # Create mock Kafka data
        data = [
            (
                "key1",
                '{"equipmentId":"EQ001","timestamp":"1700000000000","correlationId":"corr-001","schemaVersion":"1.0","payload":{"temperature":"25.5"}}',
                "test.topic",
                0,
                100,
                1700000000000,
            ),
        ]
        schema = StructType([
            StructField("key", StringType()),
            StructField("value", StringType()),
            StructField("topic", StringType()),
            StructField("partition", StringType()),
            StructField("offset", StringType()),
            StructField("timestamp", StringType()),
        ])
        kafka_df = spark.createDataFrame(data, schema)

        # Parse
        parsed_df = transformer._parse_kafka_json(kafka_df)

        # Verify
        result = parsed_df.collect()[0]
        assert result["equipmentId"] == "EQ001"
        assert result["correlationId"] == "corr-001"
        assert result["kafka_topic"] == "test.topic"

    def test_normalize_schema_extracts_metrics(self, spark, transformer):
        """Test that temperature and pressure are extracted from payload."""
        data = [
            (
                "EQ001",
                "1700000000000",
                {"temperature": "25.5", "pressure": "101.3", "equipmentType": "CVD"},
                "corr-001",
                "1.0",
                "source1",
                None,
                None,
                "test.topic",
                0,
                100,
                1700000000000,
            ),
        ]
        schema = StructType([
            StructField("equipmentId", StringType()),
            StructField("timestamp", StringType()),
            StructField("payload", StringType()),
            StructField("correlationId", StringType()),
            StructField("schemaVersion", StringType()),
            StructField("sourceSystem", StringType()),
            StructField("metricName", StringType()),
            StructField("unit", StringType()),
            StructField("kafka_topic", StringType()),
            StructField("kafka_partition", StringType()),
            StructField("kafka_offset", StringType()),
            StructField("kafka_timestamp", StringType()),
        ])

        # Create DataFrame with map column
        df = spark.createDataFrame(
            [(
                "EQ001",
                "1700000000000",
                "corr-001",
                "1.0",
                "source1",
                None,
                None,
                "test.topic",
                0,
                100,
                1700000000000,
            )],
            ["equipmentId", "timestamp", "correlationId", "schemaVersion",
             "sourceSystem", "metricName", "unit", "kafka_topic",
             "kafka_partition", "kafka_offset", "kafka_timestamp"]
        ).withColumn(
            "payload",
            F.create_map(
                F.lit("temperature"), F.lit("25.5"),
                F.lit("pressure"), F.lit("101.3"),
                F.lit("equipmentType"), F.lit("CVD"),
            )
        )

        # Normalize
        normalized_df = transformer._normalize_schema(df)

        # Verify
        result = normalized_df.collect()[0]
        assert result["temperature"] == 25.5
        assert result["pressure"] == 101.3
        assert result["equipmentType"] == "CVD"

    def test_deduplicate_keeps_latest(self, spark, transformer):
        """Test that deduplication keeps the latest event by timestamp."""
        # Create data with duplicate correlation_ids
        df = spark.createDataFrame(
            [
                ("corr-001", "EQ001", "2023-11-01 10:00:00", 100),
                ("corr-001", "EQ001", "2023-11-01 11:00:00", 200),  # Latest
                ("corr-002", "EQ002", "2023-11-01 09:00:00", 50),
            ],
            ["correlationId", "equipmentId", "event_timestamp", "kafka_offset"]
        ).withColumn(
            "event_timestamp",
            F.to_timestamp("event_timestamp")
        )

        # Deduplicate
        deduped_df = transformer._deduplicate(df)

        # Verify
        results = {row["correlationId"]: row for row in deduped_df.collect()}
        assert len(results) == 2
        assert results["corr-001"]["kafka_offset"] == 200  # Latest kept
        assert results["corr-002"]["kafka_offset"] == 50

    def test_convert_timestamps(self, spark, transformer):
        """Test timestamp conversion to Snowflake format."""
        df = spark.createDataFrame(
            [("1700000000000", 1700000000000)],
            ["timestamp", "kafka_timestamp"]
        )

        # Convert
        result_df = transformer._convert_timestamps(df)

        # Verify
        result = result_df.collect()[0]
        assert result["event_timestamp"] is not None
        assert result["kafka_ingest_timestamp"] is not None
