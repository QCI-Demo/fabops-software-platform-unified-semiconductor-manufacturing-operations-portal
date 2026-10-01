# Kafka Connect with JMX Exporter for Prometheus Metrics
FROM confluentinc/cp-kafka-connect:7.5.0

# Install Snowflake Kafka Connector
RUN confluent-hub install --no-prompt snowflakeinc/snowflake-kafka-connector:2.1.0

# Download JMX Exporter agent
ARG JMX_EXPORTER_VERSION=0.19.0
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar /opt/jmx-exporter/jmx_prometheus_javaagent.jar

# Copy JMX exporter configuration
COPY jmx-exporter-config.yaml /opt/jmx-exporter/config.yaml

# Copy Connect worker configuration
COPY connect-distributed.properties /etc/kafka-connect/connect-distributed.properties

# Set JMX exporter as Java agent
ENV KAFKA_JMX_OPTS="-javaagent:/opt/jmx-exporter/jmx_prometheus_javaagent.jar=9404:/opt/jmx-exporter/config.yaml"
ENV KAFKA_OPTS="-javaagent:/opt/jmx-exporter/jmx_prometheus_javaagent.jar=9404:/opt/jmx-exporter/config.yaml"

# Expose ports
# 8083 - Connect REST API
# 9404 - Prometheus metrics endpoint
EXPOSE 8083 9404

# Labels
LABEL maintainer="FabOps Platform Team"
LABEL description="Kafka Connect with JMX Exporter for FabOps ingestion pipelines"
