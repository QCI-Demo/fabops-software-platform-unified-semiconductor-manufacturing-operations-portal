FROM apache/spark-py:3.5.0

LABEL maintainer="FabOps Team"
LABEL description="Historical backfill Spark job for telemetry data warehouse"

USER root

# Install additional dependencies
RUN pip install --no-cache-dir \
    snowflake-connector-python>=3.5.0 \
    snowflake-sqlalchemy>=1.5.0

# Copy application code
COPY src/main/python/backfill /opt/spark/work-dir/backfill/
COPY pyproject.toml /opt/spark/work-dir/

WORKDIR /opt/spark/work-dir

# Install the backfill package
RUN pip install --no-cache-dir .

USER 185

ENV PYTHONPATH="/opt/spark/work-dir:${PYTHONPATH}"

ENTRYPOINT ["/opt/spark/bin/spark-submit", \
    "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,net.snowflake:spark-snowflake_2.12:2.15.0-spark_3.4", \
    "/opt/spark/work-dir/backfill/backfill_main.py"]
