package ru.yandex.practicum.collector.service;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.collector.model.hub.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class HubEventConverter {

    public HubEventAvro convertToAvro(HubEvent event) {
        Object payload = extractPayload(event);

        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp())
                .setPayload(payload)
                .build();
    }

    private Object extractPayload(HubEvent event) {
        if (event instanceof DeviceAddedEvent added) {
            return DeviceAddedEventAvro.newBuilder()
                    .setId(added.getId())
                    .setType(DeviceTypeAvro.valueOf(added.getDeviceType().name()))
                    .build();
        }

        if (event instanceof DeviceRemovedEvent removed) {
            return DeviceRemovedEventAvro.newBuilder()
                    .setId(removed.getId())
                    .build();
        }

        if (event instanceof ScenarioAddedEvent scenarioAdded) {
            List<ScenarioConditionAvro> conditions = scenarioAdded.getConditions().stream()
                    .map(this::convertCondition)
                    .collect(Collectors.toList());

            List<DeviceActionAvro> actions = scenarioAdded.getActions().stream()
                    .map(this::convertAction)
                    .collect(Collectors.toList());

            return ScenarioAddedEventAvro.newBuilder()
                    .setName(scenarioAdded.getName())
                    .setConditions(conditions)
                    .setActions(actions)
                    .build();
        }

        if (event instanceof ScenarioRemovedEvent scenarioRemoved) {
            return ScenarioRemovedEventAvro.newBuilder()
                    .setName(scenarioRemoved.getName())
                    .build();
        }

        throw new IllegalArgumentException("неизвестный тип события хаба: " + event.getClass());
    }

    private ScenarioConditionAvro convertCondition(ScenarioCondition condition) {
        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(condition.getValue())
                .build();
    }

    private DeviceActionAvro convertAction(DeviceAction action) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(action.getValue())
                .build();
    }
}