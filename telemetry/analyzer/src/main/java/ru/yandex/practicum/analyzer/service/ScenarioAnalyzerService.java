package ru.yandex.practicum.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.entity.*;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.*;

import com.google.protobuf.Timestamp;
import net.devh.boot.grpc.client.inject.GrpcClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ScenarioAnalyzerService {

    private final ScenarioRepository scenarioRepository;

    @GrpcClient("hub-router")
    private HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public ScenarioAnalyzerService(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    @Transactional(readOnly = true)
    public void analyze(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        Map<String, SensorStateAvro> sensorStates = snapshot.getSensorsState();

        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        if (scenarios.isEmpty()) {
            log.debug("Нет сценариев для хаба {}", hubId);
            return;
        }

        for (Scenario scenario : scenarios) {
            if (isScenarioTriggered(scenario, sensorStates)) {
                log.info("Сценарий '{}' для хаба {} активирован", scenario.getName(), hubId);
                executeScenarioActions(scenario, hubId);
            }
        }
    }

    private boolean isScenarioTriggered(Scenario scenario, Map<String, SensorStateAvro> sensorStates) {
        return scenario.getConditions().stream()
                .allMatch(sc -> checkCondition(sc, sensorStates));
    }

    private boolean checkCondition(ScenarioCondition scenarioCondition, Map<String, SensorStateAvro> sensorStates) {
        Sensor sensor = scenarioCondition.getSensor();
        Condition condition = scenarioCondition.getCondition();

        SensorStateAvro state = sensorStates.get(sensor.getId());
        if (state == null) {
            log.debug("Нет данных о датчике {}", sensor.getId());
            return false;
        }

        Object data = state.getData();
        Integer actualValue = extractActualValue(condition.getType(), data);
        if (actualValue == null) {
            log.debug("Не удалось извлечь значение для типа {}", condition.getType());
            return false;
        }

        Integer expectedValue = condition.getValue();
        if (expectedValue == null) {
            return false;
        }

        return switch (condition.getOperation()) {
            case "EQUALS" -> actualValue.equals(expectedValue);
            case "GREATER_THAN" -> actualValue > expectedValue;
            case "LOWER_THAN" -> actualValue < expectedValue;
            default -> {
                log.warn("Неизвестная операция: {}", condition.getOperation());
                yield false;
            }
        };
    }

    private Integer extractActualValue(String type, Object data) {
        return switch (type) {
            case "MOTION" -> {
                if (data instanceof MotionSensorAvro m) yield m.getMotion() ? 1 : 0;
                yield null;
            }
            case "LUMINOSITY" -> {
                if (data instanceof LightSensorAvro l) yield l.getLuminosity();
                yield null;
            }
            case "SWITCH" -> {
                if (data instanceof SwitchSensorAvro s) yield s.getState() ? 1 : 0;
                yield null;
            }
            case "TEMPERATURE" -> {
                if (data instanceof TemperatureSensorAvro t) yield t.getTemperatureC();
                if (data instanceof ClimateSensorAvro c) yield c.getTemperatureC();
                yield null;
            }
            case "CO2LEVEL" -> {
                if (data instanceof ClimateSensorAvro c) yield c.getCo2Level();
                yield null;
            }
            case "HUMIDITY" -> {
                if (data instanceof ClimateSensorAvro c) yield c.getHumidity();
                yield null;
            }
            default -> {
                log.warn("Неизвестный тип условия: {}", type);
                yield null;
            }
        };
    }

    private void executeScenarioActions(Scenario scenario, String hubId) {
        for (ScenarioAction scenarioAction : scenario.getActions()) {
            Action action = scenarioAction.getAction();
            Sensor sensor = scenarioAction.getSensor();

            try {
                DeviceActionProto actionProto = DeviceActionProto.newBuilder()
                        .setSensorId(sensor.getId())
                        .setType(ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto.valueOf(action.getType()))
                        .setValue(action.getValue() != null ? action.getValue() : 0)
                        .build();

                Instant now = Instant.now();
                DeviceActionRequest request = DeviceActionRequest.newBuilder()
                        .setHubId(hubId)
                        .setScenarioName(scenario.getName())
                        .setAction(actionProto)
                        .setTimestamp(Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build())
                        .build();

                hubRouterClient.handleDeviceAction(request);
                log.info("Команда отправлена в Hub Router: sensor={}, action={}",
                        sensor.getId(), action.getType());
            } catch (Exception e) {
                log.error("Ошибка отправки команды в Hub Router", e);
            }
        }
    }
}