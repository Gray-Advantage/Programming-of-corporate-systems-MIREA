#!/bin/sh
set -eu

BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"

for topic in text-work-added user-created role-recording-published render-requests render-responses; do
    /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "$BOOTSTRAP_SERVERS" \
        --create \
        --if-not-exists \
        --topic "$topic" \
        --partitions 1 \
        --replication-factor 1
done
