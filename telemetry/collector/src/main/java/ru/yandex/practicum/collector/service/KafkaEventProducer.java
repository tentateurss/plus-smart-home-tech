package ru.yandex.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final Producer<String, SpecificRecordBase> producer;

    public void send(String topic, String key, SpecificRecordBase event) {
        ProducerRecord<String, SpecificRecordBase> record =
                new ProducerRecord<>(topic, key, event);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Ошибка отправки сообщения в топик {}: {}", topic, exception.getMessage(), exception);
            } else {
                log.info("Сообщение отправлено в топик {} [partition={}, offset={}]",
                        topic, metadata.partition(), metadata.offset());
            }
        });
    }
}