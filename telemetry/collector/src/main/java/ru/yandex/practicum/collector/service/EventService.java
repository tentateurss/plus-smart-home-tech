package ru.yandex.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.collector.config.KafkaTopics;
import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final SensorEventConverter sensorEventConverter;
    private final HubEventConverter hubEventConverter;
    private final KafkaEventProducer kafkaEventProducer;

    public void processSensorEvent(SensorEvent event) {
        log.debug("Обработка события датчика: {}", event);

        SensorEventAvro avro = sensorEventConverter.convertToAvro(event);

        kafkaEventProducer.send(KafkaTopics.SENSORS_TOPIC, event.getHubId(), avro);

        log.info("Событие датчика {} передано на отправку в Kafka", event.getId());
    }

    public void processHubEvent(HubEvent event) {
        log.debug("Обработка события хаба: {}", event);

        HubEventAvro avro = hubEventConverter.convertToAvro(event);

        kafkaEventProducer.send(KafkaTopics.HUBS_TOPIC, event.getHubId(), avro);

        log.info("Событие хаба {} передано на отправку в Kafka", event.getHubId());
    }
}