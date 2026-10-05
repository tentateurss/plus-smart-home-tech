package ru.yandex.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.collector.config.KafkaTopics;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final SensorProtoToAvroConverter sensorConverter;
    private final HubProtoToAvroConverter hubConverter;
    private final KafkaEventProducer kafkaEventProducer;

    public void processSensorEvent(SensorEventProto event) {
        log.debug("Обработка события датчика: {}", event.getAllFields());

        SensorEventAvro avro = sensorConverter.convert(event);

        kafkaEventProducer.send(KafkaTopics.SENSORS_TOPIC, event.getHubId(), avro);

        log.info("Событие датчика {} передано на отправку в Kafka", event.getId());
    }

    public void processHubEvent(HubEventProto event) {
        log.debug("Обработка события хаба: {}", event.getAllFields());

        HubEventAvro avro = hubConverter.convert(event);

        kafkaEventProducer.send(KafkaTopics.HUBS_TOPIC, event.getHubId(), avro);

        log.info("Событие хаба {} передано на отправку в Kafka", event.getHubId());
    }
}