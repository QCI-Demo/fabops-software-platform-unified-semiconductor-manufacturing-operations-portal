"""
BackfillMain - Entry point for the historical backfill Spark job.

Reads from Kafka starting at the earliest offset, transforms telemetry events
to match the Snowflake staging schema, and writes with idempotent upsert semantics.
"""

import logging
import os
import sys
from typing import Optional

from pyspark.sql import SparkSession

from backfill.config import BackfillConfig
from backfill.kafka_reader import KafkaSourceReader
from backfill.snowflake_writer import SnowflakeUpsertWriter
from backfill.transformer import TelemetryTransformer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger(__name__)


class BackfillMain:
    """
    Main entry point for the historical backfill Spark job.

    Orchestrates reading from Kafka, transforming events, and writing to Snowflake.
    """

    def __init__(self, config: Optional[BackfillConfig] = None):
        """
        Initialize the backfill job.

        Args:
            config: Optional configuration. If None, loads from environment/Spark conf.
        """
        self.config = config or BackfillConfig.from_environment()
        self.spark: Optional[SparkSession] = None

    def create_spark_session(self) -> SparkSession:
        """
        Create and configure the SparkSession for the backfill job.

        Returns:
            Configured SparkSession with Kafka and Snowflake connectors.
        """
        builder = (
            SparkSession.builder.appName(self.config.app_name)
            .config("spark.sql.shuffle.partitions", self.config.shuffle_partitions)
            .config("spark.sql.adaptive.enabled", "true")
            .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
            .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
            # Snowflake connector options
            .config("spark.datasource.snowflake.autopushdown", "on")
        )

        # Add master configuration for local development
        if self.config.spark_master:
            builder = builder.master(self.config.spark_master)

        self.spark = builder.getOrCreate()
        self.spark.sparkContext.setLogLevel(self.config.log_level)

        logger.info(
            "SparkSession created: app_name=%s, version=%s",
            self.config.app_name,
            self.spark.version,
        )
        return self.spark

    def run(self) -> None:
        """
        Execute the backfill job.

        Reads all historical data from Kafka, transforms to staging schema,
        and writes to Snowflake with MERGE-based upsert for idempotency.
        """
        if not self.spark:
            self.create_spark_session()

        logger.info("Starting historical backfill job")
        logger.info(
            "Kafka config: servers=%s, topic=%s, starting_offset=%s",
            self.config.kafka_bootstrap_servers,
            self.config.kafka_topic,
            self.config.kafka_starting_offset,
        )
        logger.info(
            "Snowflake config: database=%s, schema=%s, table=%s",
            self.config.snowflake_database,
            self.config.snowflake_schema,
            self.config.snowflake_table,
        )

        try:
            # Read from Kafka starting at earliest offset
            kafka_reader = KafkaSourceReader(self.spark, self.config)
            raw_df = kafka_reader.read_from_earliest()
            logger.info("Read Kafka batch with %d partitions", raw_df.rdd.getNumPartitions())

            # Transform to staging schema
            transformer = TelemetryTransformer(self.spark, self.config)
            staging_df = transformer.transform(raw_df)
            logger.info("Transformed to staging schema")

            # Write to Snowflake with MERGE upsert
            writer = SnowflakeUpsertWriter(self.spark, self.config)
            rows_affected = writer.upsert(staging_df)
            logger.info("Backfill complete: rows_affected=%d", rows_affected)

        except Exception as e:
            logger.exception("Backfill job failed: %s", str(e))
            raise

        finally:
            if self.spark:
                self.spark.stop()
                logger.info("SparkSession stopped")


def main() -> None:
    """CLI entry point for the backfill job."""
    job = BackfillMain()
    job.run()


if __name__ == "__main__":
    main()
