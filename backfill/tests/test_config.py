"""Tests for backfill configuration."""

import os
from unittest import mock

import pytest

from backfill.config import BackfillConfig


class TestBackfillConfig:
    """Test cases for BackfillConfig."""

    def test_default_values(self):
        """Test that default configuration values are set correctly."""
        config = BackfillConfig()

        assert config.app_name == "fabops-historical-backfill"
        assert config.kafka_bootstrap_servers == "localhost:9092"
        assert config.kafka_topic == "fabops.telemetry.raw"
        assert config.kafka_starting_offset == "earliest"
        assert config.snowflake_database == "FABOPS_DW"
        assert config.snowflake_schema == "STAGING"
        assert config.snowflake_table == "TELEMETRY_EVENTS"
        assert config.merge_key_columns == ["correlation_id"]

    def test_from_environment(self):
        """Test configuration loading from environment variables."""
        env_vars = {
            "BACKFILL_APP_NAME": "test-backfill",
            "BACKFILL_KAFKA_BOOTSTRAP_SERVERS": "kafka1:9092,kafka2:9092",
            "BACKFILL_KAFKA_TOPIC": "test.topic",
            "BACKFILL_SNOWFLAKE_URL": "account.snowflakecomputing.com",
            "BACKFILL_SNOWFLAKE_DATABASE": "TEST_DB",
            "BACKFILL_BATCH_SIZE": "5000",
        }

        with mock.patch.dict(os.environ, env_vars, clear=False):
            config = BackfillConfig.from_environment()

            assert config.app_name == "test-backfill"
            assert config.kafka_bootstrap_servers == "kafka1:9092,kafka2:9092"
            assert config.kafka_topic == "test.topic"
            assert config.snowflake_url == "account.snowflakecomputing.com"
            assert config.snowflake_database == "TEST_DB"
            assert config.batch_size == 5000

    def test_get_kafka_options(self):
        """Test Kafka options dictionary generation."""
        config = BackfillConfig(
            kafka_bootstrap_servers="kafka:9092",
            kafka_topic="test.topic",
            kafka_starting_offset="earliest",
            kafka_consumer_group="test-group",
        )

        options = config.get_kafka_options()

        assert options["kafka.bootstrap.servers"] == "kafka:9092"
        assert options["subscribe"] == "test.topic"
        assert options["startingOffsets"] == "earliest"
        assert options["kafka.group.id"] == "test-group"
        assert options["failOnDataLoss"] == "false"

    def test_get_snowflake_options(self):
        """Test Snowflake options dictionary generation."""
        config = BackfillConfig(
            snowflake_url="account.snowflakecomputing.com",
            snowflake_user="test_user",
            snowflake_database="TEST_DB",
            snowflake_schema="TEST_SCHEMA",
            snowflake_warehouse="TEST_WH",
            snowflake_role="TEST_ROLE",
        )

        options = config.get_snowflake_options()

        assert options["sfURL"] == "account.snowflakecomputing.com"
        assert options["sfUser"] == "test_user"
        assert options["sfDatabase"] == "TEST_DB"
        assert options["sfSchema"] == "TEST_SCHEMA"
        assert options["sfWarehouse"] == "TEST_WH"
        assert options["sfRole"] == "TEST_ROLE"

    def test_merge_key_columns_from_env(self):
        """Test comma-separated merge key columns from environment."""
        env_vars = {
            "BACKFILL_MERGE_KEY_COLUMNS": "correlation_id,equipment_id",
        }

        with mock.patch.dict(os.environ, env_vars, clear=False):
            config = BackfillConfig.from_environment()

            assert config.merge_key_columns == ["correlation_id", "equipment_id"]
