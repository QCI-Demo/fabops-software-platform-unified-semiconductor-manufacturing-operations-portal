"""Tests for Snowflake MERGE upsert writer."""

import pytest

from backfill.config import BackfillConfig
from backfill.snowflake_writer import SnowflakeUpsertWriter


@pytest.fixture
def config():
    """Create test configuration."""
    return BackfillConfig(
        snowflake_url="test.snowflakecomputing.com",
        snowflake_user="test_user",
        snowflake_database="TEST_DB",
        snowflake_schema="TEST_SCHEMA",
        snowflake_table="TEST_TABLE",
        merge_key_columns=["correlation_id"],
    )


class TestSnowflakeUpsertWriter:
    """Test cases for SnowflakeUpsertWriter."""

    def test_build_merge_sql_single_key(self, config):
        """Test MERGE SQL generation with single merge key."""
        # Create writer without spark session (only testing SQL generation)
        writer = SnowflakeUpsertWriter(spark=None, config=config)

        merge_sql = writer._build_merge_sql()

        # Verify key components
        assert "MERGE INTO TEST_DB.TEST_SCHEMA.TEST_TABLE AS target" in merge_sql
        assert "USING TEST_DB.TEST_SCHEMA._backfill_staging_temp AS source" in merge_sql
        assert "ON target.correlation_id = source.correlation_id" in merge_sql
        assert "WHEN MATCHED THEN" in merge_sql
        assert "UPDATE SET" in merge_sql
        assert "WHEN NOT MATCHED THEN" in merge_sql
        assert "INSERT" in merge_sql

    def test_build_merge_sql_multiple_keys(self):
        """Test MERGE SQL generation with composite merge key."""
        config = BackfillConfig(
            snowflake_database="TEST_DB",
            snowflake_schema="TEST_SCHEMA",
            snowflake_table="TEST_TABLE",
            merge_key_columns=["correlation_id", "equipment_id"],
        )
        writer = SnowflakeUpsertWriter(spark=None, config=config)

        merge_sql = writer._build_merge_sql()

        # Verify composite key join condition
        assert "target.correlation_id = source.correlation_id" in merge_sql
        assert "target.equipment_id = source.equipment_id" in merge_sql
        assert " AND " in merge_sql  # Keys joined with AND

    def test_get_target_table_fqn(self, config):
        """Test fully qualified table name generation."""
        writer = SnowflakeUpsertWriter(spark=None, config=config)

        fqn = writer._get_target_table_fqn()

        assert fqn == "TEST_DB.TEST_SCHEMA.TEST_TABLE"

    def test_get_temp_table_fqn(self, config):
        """Test temporary table name generation."""
        writer = SnowflakeUpsertWriter(spark=None, config=config)

        fqn = writer._get_temp_table_fqn()

        assert fqn == "TEST_DB.TEST_SCHEMA._backfill_staging_temp"

    def test_merge_sql_contains_all_columns(self, config):
        """Test that MERGE SQL includes all required columns."""
        writer = SnowflakeUpsertWriter(spark=None, config=config)

        merge_sql = writer._build_merge_sql()

        required_columns = [
            "equipment_id",
            "equipment_type",
            "event_timestamp",
            "temperature",
            "pressure",
            "error_codes",
            "payload_json",
            "kafka_partition",
            "kafka_offset",
            "backfill_timestamp",
        ]

        for col in required_columns:
            assert col in merge_sql, f"Missing column: {col}"
