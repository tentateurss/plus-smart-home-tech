package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.repository.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubEventService {

    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;

    @Transactional
    public void processHubEvent(HubEventAvro event) {
        Object payload = event.getPayload();

        if (payload instanceof DeviceAddedEventAvro added) {
            handleDeviceAdded(event.getHubId(), added);
        } else if (payload instanceof DeviceRemovedEventAvro removed) {
            handleDeviceRemoved(event.getHubId(), removed);
        } else if (payload instanceof ScenarioAddedEventAvro scenarioAdded) {
            handleScenarioAdded(event.getHubId(), scenarioAdded);
        } else if (payload instanceof ScenarioRemovedEventAvro scenarioRemoved) {
            handleScenarioRemoved(event.getHubId(), scenarioRemoved);
        } else {
            log.warn("Неизвестный тип события хаба: {}", payload.getClass());
        }
    }

    private void handleDeviceAdded(String hubId, DeviceAddedEventAvro event) {
        if (sensorRepository.findByIdAndHubId(event.getId(), hubId).isPresent()) {
            log.debug("Датчик {} уже существует в хабе {}", event.getId(), hubId);
            return;
        }

        Sensor sensor = Sensor.builder()
                .id(event.getId())
                .hubId(hubId)
                .build();
        sensorRepository.save(sensor);
        log.info("Датчик {} добавлен в хаб {}", event.getId(), hubId);
    }

    private void handleDeviceRemoved(String hubId, DeviceRemovedEventAvro event) {
        sensorRepository.findByIdAndHubId(event.getId(), hubId)
                .ifPresent(sensor -> {
                    sensorRepository.delete(sensor);
                    log.info("Датчик {} удалён из хаба {}", event.getId(), hubId);
                });
    }

    private void handleScenarioAdded(String hubId, ScenarioAddedEventAvro event) {
        Optional<Scenario> existing = scenarioRepository.findByHubIdAndName(hubId, event.getName());
        existing.ifPresent(scenarioRepository::delete);

        Scenario scenario = Scenario.builder()
                .hubId(hubId)
                .name(event.getName())
                .conditions(new HashSet<>())
                .actions(new HashSet<>())
                .build();
        scenario = scenarioRepository.save(scenario);

        Set<ScenarioCondition> conditions = new HashSet<>();
        for (ScenarioConditionAvro conditionAvro : event.getConditions()) {
            Condition condition = conditionRepository.save(Condition.builder()
                    .type(conditionAvro.getType().name())
                    .operation(conditionAvro.getOperation().name())
                    .value(extractIntValue(conditionAvro.getValue()))
                    .build());

            Sensor sensor = sensorRepository.findByIdAndHubId(conditionAvro.getSensorId(), hubId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Датчик не найден: " + conditionAvro.getSensorId()));

            conditions.add(ScenarioCondition.builder()
                    .scenario(scenario)
                    .sensor(sensor)
                    .condition(condition)
                    .build());
        }
        scenario.setConditions(conditions);

        Set<ScenarioAction> actions = new HashSet<>();
        for (DeviceActionAvro actionAvro : event.getActions()) {
            Action action = actionRepository.save(Action.builder()
                    .type(actionAvro.getType().name())
                    .value(actionAvro.getValue())
                    .build());

            Sensor sensor = sensorRepository.findByIdAndHubId(actionAvro.getSensorId(), hubId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Датчик не найден: " + actionAvro.getSensorId()));

            actions.add(ScenarioAction.builder()
                    .scenario(scenario)
                    .sensor(sensor)
                    .action(action)
                    .build());
        }
        scenario.setActions(actions);

        scenarioRepository.save(scenario);
        log.info("Сценарий '{}' добавлен в хаб {}", event.getName(), hubId);
    }

    private void handleScenarioRemoved(String hubId, ScenarioRemovedEventAvro event) {
        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(scenario -> {
                    scenarioRepository.delete(scenario);
                    log.info("Сценарий '{}' удалён из хаба {}", event.getName(), hubId);
                });
    }

    private Integer extractIntValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return null;
    }
}