# FabOps Backfill Job with OpenTelemetry
FROM apache/spark:3.5.0-python3

USER root

# Install Python dependencies
RUN pip install --no-cache-dir \
    opentelemetry-api==1.21.0 \
    opentelemetry-sdk==1.21.0 \
    opentelemetry-exporter-prometheus==0.42b0 \
    prometheus-client==0.19.0 \
    pyspark==3.5.0 \
    confluent-kafka==2.3.0

# Create app directory
WORKDIR /app

# Copy application code
COPY otel_metrics.py /app/
COPY backfill_job.py /app/

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV SPARK_HOME=/opt/spark
ENV PYSPARK_PYTHON=python3

# Expose Prometheus metrics port
EXPOSE 9464

# Labels
LABEL maintainer="FabOps Platform Team"
LABEL description="FabOps Backfill Job with OpenTelemetry instrumentation"

USER spark

# Run the backfill job
ENTRYPOINT ["spark-submit", \
    "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,net.snowflake:spark-snowflake_2.12:2.12.0-spark_3.4", \
    "--conf", "spark.metrics.conf.*.sink.prometheusServlet.class=org.apache.spark.metrics.sink.PrometheusServlet", \
    "--conf", "spark.metrics.conf.*.sink.prometheusServlet.path=/metrics", \
    "--conf", "spark.ui.prometheus.enabled=true", \
    "/app/backfill_job.py"]
