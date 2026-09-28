"""
Kafka source reader for the backfill job.

Reads historical telemetry events from Kafka starting at the earliest offset.
"""

import logging
from typing import Optional

from pyspark.sql import DataFrame, SparkSession

from backfill.config import BackfillConfig

logger = logging.getLogger(__name__)


class KafkaSourceReader:
    """
    Reads telemetry events from Kafka using Spark's batch mode.

    Configured to start from the earliest offset to capture full history.
    """

    def __init__(self, spark: SparkSession, config: BackfillConfig):
        """
        Initialize the Kafka reader.

        Args:
            spark: Active SparkSession.
            config: Backfill configuration containing Kafka settings.
        """
        self.spark = spark
        self.config = config

    def read_from_earliest(self) -> DataFrame:
        """
        Read all messages from Kafka topic starting at the earliest offset.

        Returns:
            DataFrame with Kafka message columns (key, value, topic, partition,
            offset, timestamp, timestampType).
        """
        kafka_options = self.config.get_kafka_options()

        logger.info(
            "Reading from Kafka: topic=%s, starting_offset=%s",
            self.config.kafka_topic,
            self.config.kafka_starting_offset,
        )

        df = (
            self.spark.read.format("kafka")
            .options(**kafka_options)
            .load()
        )

        record_count = df.count()
        logger.info("Loaded %d records from Kafka", record_count)

        return df

    def read_with_offset_range(
        self,
        starting_offsets: str,
        ending_offsets: Optional[str] = None,
    ) -> DataFrame:
        """
        Read messages from Kafka within a specific offset range.

        Args:
            starting_offsets: JSON string specifying starting offsets per partition
                              or "earliest" / "latest".
            ending_offsets: JSON string specifying ending offsets per partition
                           or "latest". If None, uses config default.

        Returns:
            DataFrame with Kafka message columns.

        Example:
            # Read specific partition ranges
            reader.read_with_offset_range(
                starting_offsets='{"fabops.telemetry.raw":{"0":0,"1":0}}',
                ending_offsets='{"fabops.telemetry.raw":{"0":1000,"1":1000}}'
            )
        """
        options = self.config.get_kafka_options()
        options["startingOffsets"] = starting_offsets

        if ending_offsets:
            options["endingOffsets"] = ending_offsets

        logger.info(
            "Reading from Kafka with offset range: start=%s, end=%s",
            starting_offsets,
            ending_offsets or self.config.kafka_ending_offset,
        )

        return self.spark.read.format("kafka").options(**options).load()
