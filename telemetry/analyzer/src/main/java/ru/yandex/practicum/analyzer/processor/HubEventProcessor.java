package ru.yandex.practicum.analyzer.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.service.HubEventService;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private static final String HUBS_TOPIC = "telemetry.hubs.v1";

    private final Consumer<String, HubEventAvro> hubEventConsumer;
    private final HubEventService hubEventService;

    @Override
    public void run() {
        try {
            hubEventConsumer.subscribe(List.of(HUBS_TOPIC));
            log.info("HubEventProcessor подписан на топик {}", HUBS_TOPIC);

            while (true) {
                ConsumerRecords<String, HubEventAvro> records =
                        hubEventConsumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    HubEventAvro event = record.value();
                    log.debug("Получено событие хаба {}: {}", event.getHubId(), event.getPayload());

                    try {
                        hubEventService.processHubEvent(event);
                    } catch (Exception e) {
                        log.error("Ошибка обработки события хаба", e);
                    }
                }

                if (!records.isEmpty()) {
                    hubEventConsumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
            // игнорируем
        } catch (Exception e) {
            log.error("Ошибка в цикле обработки событий хабов", e);
        } finally {
            try {
                hubEventConsumer.commitSync();
            } finally {
                log.info("Закрываем HubEventProcessor");
                hubEventConsumer.close();
            }
        }
    }
}