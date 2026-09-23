package ru.yandex.practicum.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {

    private static final String SENSORS_TOPIC = "telemetry.sensors.v1";
    private static final String SNAPSHOTS_TOPIC = "telemetry.snapshots.v1";

    private final Consumer<String, SensorEventAvro> consumer;
    private final Producer<String, SpecificRecordBase> producer;
    private final SnapshotService snapshotService;

    public void start() {
        try {
            consumer.subscribe(List.of(SENSORS_TOPIC));
            log.info("Aggregator подписан на топик {}", SENSORS_TOPIC);

            while (true) {
                ConsumerRecords<String, SensorEventAvro> records =
                        consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    SensorEventAvro event = record.value();
                    log.debug("Получено событие от датчика {} хаба {}",
                            event.getId(), event.getHubId());

                    Optional<SensorsSnapshotAvro> updatedSnapshot =
                            snapshotService.updateState(event);

                    updatedSnapshot.ifPresent(snapshot -> {
                        ProducerRecord<String, SpecificRecordBase> producerRecord =
                                new ProducerRecord<>(SNAPSHOTS_TOPIC, snapshot.getHubId(), snapshot);
                        producer.send(producerRecord, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Ошибка отправки снапшота в топик {}",
                                        SNAPSHOTS_TOPIC, exception);
                            } else {
                                log.info("Снапшот хаба {} отправлен в топик {} [partition={}, offset={}]",
                                        snapshot.getHubId(), SNAPSHOTS_TOPIC,
                                        metadata.partition(), metadata.offset());
                            }
                        });
                    });
                }

                if (!records.isEmpty()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка во время обработки событий от датчиков", e);
        } finally {
            try {
                producer.flush();
                consumer.commitSync();
            } finally {
                log.info("Закрываем консьюмер");
                consumer.close();
                log.info("Закрываем продюсер");
                producer.close();
            }
        }
    }
}