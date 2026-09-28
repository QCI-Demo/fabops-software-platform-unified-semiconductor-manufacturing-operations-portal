"""
Snowflake writer with idempotent MERGE upsert semantics.

Writes transformed telemetry data to Snowflake staging tables using
MERGE to ensure idempotent replay handling.
"""

import logging
from typing import Optional

from pyspark.sql import DataFrame, SparkSession

from backfill.config import BackfillConfig

logger = logging.getLogger(__name__)


class SnowflakeUpsertWriter:
    """
    Writes DataFrames to Snowflake using MERGE for idempotent upserts.

    The MERGE statement ensures that:
    - New records are inserted
    - Existing records (by correlation_id) are updated
    - Replays of the same data are handled gracefully
    """

    def __init__(self, spark: SparkSession, config: BackfillConfig):
        """
        Initialize the Snowflake writer.

        Args:
            spark: Active SparkSession with Snowflake connector.
            config: Backfill configuration containing Snowflake settings.
        """
        self.spark = spark
        self.config = config
        self._temp_table_name = "_backfill_staging_temp"

    def upsert(self, df: DataFrame) -> int:
        """
        Perform idempotent upsert using Snowflake MERGE.

        The process:
        1. Write DataFrame to a temporary Snowflake table
        2. Execute MERGE INTO target FROM temp
        3. Drop the temporary table
        4. Return count of affected rows

        Args:
            df: Transformed DataFrame to upsert.

        Returns:
            Number of rows affected (inserted + updated).
        """
        record_count = df.count()
        logger.info("Starting upsert for %d records", record_count)

        if record_count == 0:
            logger.info("No records to upsert, skipping")
            return 0

        try:
            # Write to temporary staging table
            self._write_to_temp_table(df)

            # Execute MERGE statement
            rows_affected = self._execute_merge()

            logger.info("Upsert complete: rows_affected=%d", rows_affected)
            return rows_affected

        finally:
            # Cleanup temporary table
            self._drop_temp_table()

    def _write_to_temp_table(self, df: DataFrame) -> None:
        """Write DataFrame to temporary Snowflake table."""
        snowflake_options = self.config.get_snowflake_options()
        snowflake_options["dbtable"] = self._get_temp_table_fqn()

        logger.info("Writing to temp table: %s", self._get_temp_table_fqn())

        (
            df.write.format("snowflake")
            .options(**snowflake_options)
            .mode("overwrite")
            .save()
        )

    def _execute_merge(self) -> int:
        """
        Execute MERGE statement for idempotent upsert.

        Returns:
            Number of rows affected.
        """
        merge_sql = self._build_merge_sql()
        logger.debug("Executing MERGE SQL: %s", merge_sql)

        snowflake_options = self.config.get_snowflake_options()

        # Execute MERGE via Snowflake connector
        result_df = (
            self.spark.read.format("snowflake")
            .options(**snowflake_options)
            .option("query", merge_sql)
            .load()
        )

        # MERGE returns row counts in result
        rows = result_df.collect()
        if rows:
            # Snowflake MERGE returns: number of rows inserted, number of rows updated
            return sum(row[0] for row in rows if row[0])
        return 0

    def _build_merge_sql(self) -> str:
        """
        Build the Snowflake MERGE SQL statement.

        Uses correlation_id as the merge key for idempotent updates.
        """
        target_table = self._get_target_table_fqn()
        temp_table = self._get_temp_table_fqn()
        merge_keys = self.config.merge_key_columns

        # Build join condition
        join_conditions = " AND ".join(
            f"target.{col} = source.{col}" for col in merge_keys
        )

        # Column list for UPDATE and INSERT
        update_columns = [
            "equipment_id",
            "equipment_type",
            "event_timestamp",
            "temperature",
            "pressure",
            "error_codes",
            "payload_json",
            "schema_version",
            "source_system",
            "metric_name",
            "unit",
            "kafka_partition",
            "kafka_offset",
            "kafka_ingest_timestamp",
            "backfill_timestamp",
            "backfill_batch_id",
            "source_topic",
        ]

        # Build UPDATE SET clause
        update_set = ", ".join(
            f"{col} = source.{col}" for col in update_columns
        )

        # Build INSERT columns and values
        all_columns = merge_keys + update_columns
        insert_columns = ", ".join(all_columns)
        insert_values = ", ".join(f"source.{col}" for col in all_columns)

        merge_sql = f"""
        MERGE INTO {target_table} AS target
        USING {temp_table} AS source
        ON {join_conditions}
        WHEN MATCHED THEN
            UPDATE SET {update_set}
        WHEN NOT MATCHED THEN
            INSERT ({insert_columns})
            VALUES ({insert_values})
        """

        return merge_sql.strip()

    def _get_target_table_fqn(self) -> str:
        """Get fully qualified name of target table."""
        return (
            f"{self.config.snowflake_database}."
            f"{self.config.snowflake_schema}."
            f"{self.config.snowflake_table}"
        )

    def _get_temp_table_fqn(self) -> str:
        """Get fully qualified name of temporary staging table."""
        return (
            f"{self.config.snowflake_database}."
            f"{self.config.snowflake_schema}."
            f"{self._temp_table_name}"
        )

    def _drop_temp_table(self) -> None:
        """Drop the temporary staging table."""
        drop_sql = f"DROP TABLE IF EXISTS {self._get_temp_table_fqn()}"
        logger.debug("Dropping temp table: %s", drop_sql)

        try:
            snowflake_options = self.config.get_snowflake_options()
            self.spark.read.format("snowflake").options(**snowflake_options).option(
                "query", drop_sql
            ).load()
        except Exception as e:
            # Log but don't fail on cleanup errors
            logger.warning("Failed to drop temp table: %s", str(e))

    def append_only(self, df: DataFrame) -> int:
        """
        Simple append write without MERGE (for initial loads).

        Args:
            df: DataFrame to write.

        Returns:
            Number of rows written.
        """
        snowflake_options = self.config.get_snowflake_options()
        snowflake_options["dbtable"] = self._get_target_table_fqn()

        record_count = df.count()
        logger.info("Appending %d records to %s", record_count, self._get_target_table_fqn())

        (
            df.write.format("snowflake")
            .options(**snowflake_options)
            .mode("append")
            .save()
        )

        return record_count
