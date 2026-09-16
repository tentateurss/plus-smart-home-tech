package ru.yandex.practicum.collector.config;

public final class KafkaTopics {

    public static final String SENSORS_TOPIC = "telemetry.sensors.v1";
    public static final String HUBS_TOPIC = "telemetry.hubs.v1";

    private KafkaTopics() {
    }
}