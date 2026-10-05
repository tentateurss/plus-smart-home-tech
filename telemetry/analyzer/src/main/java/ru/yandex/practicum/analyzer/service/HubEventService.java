package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.repository.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        scenarioRepository.findByHubIdAndName(hubId, event.getName())
                .ifPresent(scenarioRepository::delete);

        Scenario scenario = Scenario.builder()
                .hubId(hubId)
                .name(event.getName())
                .conditions(new HashSet<>())
                .actions(new HashSet<>())
                .build();
        scenario = scenarioRepository.save(scenario);

        Set<String> sensorIds = new HashSet<>();
        event.getConditions().forEach(c -> sensorIds.add(c.getSensorId()));
        event.getActions().forEach(a -> sensorIds.add(a.getSensorId()));

        Map<String, Sensor> sensorMap = sensorRepository.findAllByIdInAndHubId(sensorIds, hubId)
                .stream()
                .collect(Collectors.toMap(Sensor::getId, Function.identity()));

        for (String sensorId : sensorIds) {
            if (!sensorMap.containsKey(sensorId)) {
                throw new IllegalArgumentException("Датчик не найден: " + sensorId);
            }
        }

        List<Condition> conditionsToSave = event.getConditions().stream()
                .map(c -> Condition.builder()
                        .type(c.getType().name())
                        .operation(c.getOperation().name())
                        .value(extractIntValue(c.getValue()))
                        .build())
                .toList();
        List<Condition> savedConditions = conditionRepository.saveAll(conditionsToSave);

        List<Action> actionsToSave = event.getActions().stream()
                .map(a -> Action.builder()
                        .type(a.getType().name())
                        .value(a.getValue())
                        .build())
                .toList();
        List<Action> savedActions = actionRepository.saveAll(actionsToSave);

        Set<ScenarioCondition> scenarioConditions = new HashSet<>();
        List<ScenarioConditionAvro> conditionAvros = event.getConditions();
        for (int i = 0; i < conditionAvros.size(); i++) {
            ScenarioConditionAvro conditionAvro = conditionAvros.get(i);
            scenarioConditions.add(ScenarioCondition.builder()
                    .scenario(scenario)
                    .sensor(sensorMap.get(conditionAvro.getSensorId()))
                    .condition(savedConditions.get(i))
                    .build());
        }
        scenario.setConditions(scenarioConditions);

        Set<ScenarioAction> scenarioActions = new HashSet<>();
        List<DeviceActionAvro> actionAvros = event.getActions();
        for (int i = 0; i < actionAvros.size(); i++) {
            DeviceActionAvro actionAvro = actionAvros.get(i);
            scenarioActions.add(ScenarioAction.builder()
                    .scenario(scenario)
                    .sensor(sensorMap.get(actionAvro.getSensorId()))
                    .action(savedActions.get(i))
                    .build());
        }
        scenario.setActions(scenarioActions);

        scenarioRepository.save(scenario);
        log.info("Сценарий '{}' добавлен в хаб {} ({} условий, {} действий)",
                event.getName(), hubId, savedConditions.size(), savedActions.size());
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