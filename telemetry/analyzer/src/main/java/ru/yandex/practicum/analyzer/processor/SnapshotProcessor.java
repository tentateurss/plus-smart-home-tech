package ru.yandex.practicum.analyzer.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.service.ScenarioAnalyzerService;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {

    private static final String SNAPSHOTS_TOPIC = "telemetry.snapshots.v1";

    private final Consumer<String, SensorsSnapshotAvro> snapshotConsumer;
    private final ScenarioAnalyzerService scenarioAnalyzerService;

    public void start() {
        try {
            snapshotConsumer.subscribe(List.of(SNAPSHOTS_TOPIC));
            log.info("SnapshotProcessor подписан на топик {}", SNAPSHOTS_TOPIC);

            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records =
                        snapshotConsumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    SensorsSnapshotAvro snapshot = record.value();
                    log.debug("Получен снапшот хаба {}", snapshot.getHubId());

                    try {
                        scenarioAnalyzerService.analyze(snapshot);
                    } catch (Exception e) {
                        log.error("Ошибка анализа снапшота", e);
                    }
                }

                if (!records.isEmpty()) {
                    snapshotConsumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
            // игнорируем
        } catch (Exception e) {
            log.error("Ошибка в цикле обработки снапшотов", e);
        } finally {
            try {
                snapshotConsumer.commitSync();
            } finally {
                log.info("Закрываем SnapshotProcessor");
                snapshotConsumer.close();
            }
        }
    }
}