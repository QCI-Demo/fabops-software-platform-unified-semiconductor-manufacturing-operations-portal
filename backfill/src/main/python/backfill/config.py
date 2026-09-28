"""
Configuration management for the backfill job.

Loads settings from environment variables, Spark configuration, or defaults.
"""

import os
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class BackfillConfig:
    """
    Configuration for the historical backfill Spark job.
    """

    # Application settings
    app_name: str = "fabops-historical-backfill"
    spark_master: Optional[str] = None
    shuffle_partitions: int = 200
    log_level: str = "INFO"
    batch_size: int = 10000

    # Kafka settings
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_topic: str = "fabops.telemetry.raw"
    kafka_starting_offset: str = "earliest"
    kafka_ending_offset: str = "latest"
    kafka_consumer_group: str = "fabops-backfill-consumer"
    kafka_security_protocol: str = "PLAINTEXT"
    kafka_sasl_mechanism: Optional[str] = None
    kafka_sasl_jaas_config: Optional[str] = None

    # Snowflake settings
    snowflake_url: str = ""
    snowflake_user: str = ""
    snowflake_private_key_path: Optional[str] = None
    snowflake_database: str = "FABOPS_DW"
    snowflake_schema: str = "STAGING"
    snowflake_warehouse: str = "BACKFILL_WH"
    snowflake_table: str = "TELEMETRY_EVENTS"
    snowflake_role: str = "BACKFILL_ROLE"

    # Upsert/MERGE settings
    merge_key_columns: list[str] = field(
        default_factory=lambda: ["correlation_id"]
    )
    dedup_order_column: str = "event_timestamp"

    @classmethod
    def from_environment(cls) -> "BackfillConfig":
        """
        Create configuration from environment variables.

        Environment variables are prefixed with BACKFILL_ and mapped to
        configuration fields (e.g., BACKFILL_KAFKA_BOOTSTRAP_SERVERS).
        """
        def get_env(key: str, default: str = "") -> str:
            return os.environ.get(f"BACKFILL_{key}", default) or default

        def get_env_int(key: str, default: int) -> int:
            val = os.environ.get(f"BACKFILL_{key}")
            return int(val) if val else default

        def get_env_list(key: str, default: list[str]) -> list[str]:
            val = os.environ.get(f"BACKFILL_{key}")
            return val.split(",") if val else default

        return cls(
            app_name=get_env("APP_NAME", "fabops-historical-backfill"),
            spark_master=os.environ.get("SPARK_MASTER"),
            shuffle_partitions=get_env_int("SHUFFLE_PARTITIONS", 200),
            log_level=get_env("LOG_LEVEL", "INFO"),
            batch_size=get_env_int("BATCH_SIZE", 10000),
            # Kafka
            kafka_bootstrap_servers=get_env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            kafka_topic=get_env("KAFKA_TOPIC", "fabops.telemetry.raw"),
            kafka_starting_offset=get_env("KAFKA_STARTING_OFFSET", "earliest"),
            kafka_ending_offset=get_env("KAFKA_ENDING_OFFSET", "latest"),
            kafka_consumer_group=get_env("KAFKA_CONSUMER_GROUP", "fabops-backfill-consumer"),
            kafka_security_protocol=get_env("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT"),
            kafka_sasl_mechanism=os.environ.get("BACKFILL_KAFKA_SASL_MECHANISM"),
            kafka_sasl_jaas_config=os.environ.get("BACKFILL_KAFKA_SASL_JAAS_CONFIG"),
            # Snowflake
            snowflake_url=get_env("SNOWFLAKE_URL", ""),
            snowflake_user=get_env("SNOWFLAKE_USER", ""),
            snowflake_private_key_path=os.environ.get("BACKFILL_SNOWFLAKE_PRIVATE_KEY_PATH"),
            snowflake_database=get_env("SNOWFLAKE_DATABASE", "FABOPS_DW"),
            snowflake_schema=get_env("SNOWFLAKE_SCHEMA", "STAGING"),
            snowflake_warehouse=get_env("SNOWFLAKE_WAREHOUSE", "BACKFILL_WH"),
            snowflake_table=get_env("SNOWFLAKE_TABLE", "TELEMETRY_EVENTS"),
            snowflake_role=get_env("SNOWFLAKE_ROLE", "BACKFILL_ROLE"),
            # MERGE settings
            merge_key_columns=get_env_list("MERGE_KEY_COLUMNS", ["correlation_id"]),
            dedup_order_column=get_env("DEDUP_ORDER_COLUMN", "event_timestamp"),
        )

    def get_snowflake_options(self) -> dict:
        """
        Get Snowflake connector options for Spark DataFrame read/write.

        Returns:
            Dictionary of Snowflake connection parameters.
        """
        options = {
            "sfURL": self.snowflake_url,
            "sfUser": self.snowflake_user,
            "sfDatabase": self.snowflake_database,
            "sfSchema": self.snowflake_schema,
            "sfWarehouse": self.snowflake_warehouse,
            "sfRole": self.snowflake_role,
        }

        if self.snowflake_private_key_path:
            options["pem_private_key"] = self._read_private_key()

        return options

    def _read_private_key(self) -> str:
        """Read private key from file for key-pair authentication."""
        if not self.snowflake_private_key_path:
            return ""
        with open(self.snowflake_private_key_path, "r") as f:
            return f.read()

    def get_kafka_options(self) -> dict:
        """
        Get Kafka source options for Spark Structured Streaming / batch.

        Returns:
            Dictionary of Kafka connection parameters.
        """
        options = {
            "kafka.bootstrap.servers": self.kafka_bootstrap_servers,
            "subscribe": self.kafka_topic,
            "startingOffsets": self.kafka_starting_offset,
            "endingOffsets": self.kafka_ending_offset,
            "kafka.group.id": self.kafka_consumer_group,
            "kafka.security.protocol": self.kafka_security_protocol,
            "failOnDataLoss": "false",  # Allow graceful handling of missing offsets
        }

        if self.kafka_sasl_mechanism:
            options["kafka.sasl.mechanism"] = self.kafka_sasl_mechanism

        if self.kafka_sasl_jaas_config:
            options["kafka.sasl.jaas.config"] = self.kafka_sasl_jaas_config

        return options
