package ru.yandex.practicum.collector.service;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;
import java.util.List;

@Component
public class HubProtoToAvroConverter {

    public HubEventAvro convert(HubEventProto event) {
        Object payload = extractPayload(event);

        Instant timestamp = Instant.ofEpochSecond(
                event.getTimestamp().getSeconds(),
                event.getTimestamp().getNanos()
        );

        return HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(timestamp)
                .setPayload(payload)
                .build();
    }

    private Object extractPayload(HubEventProto event) {
        return switch (event.getPayloadCase()) {
            case DEVICE_ADDED -> convertDeviceAdded(event.getDeviceAdded());
            case DEVICE_REMOVED -> convertDeviceRemoved(event.getDeviceRemoved());
            case SCENARIO_ADDED -> convertScenarioAdded(event.getScenarioAdded());
            case SCENARIO_REMOVED -> convertScenarioRemoved(event.getScenarioRemoved());
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события хаба: " + event.getPayloadCase());
        };
    }

    private DeviceAddedEventAvro convertDeviceAdded(DeviceAddedEventProto proto) {
        return DeviceAddedEventAvro.newBuilder()
                .setId(proto.getId())
                .setType(DeviceTypeAvro.valueOf(proto.getType().name()))
                .build();
    }

    private DeviceRemovedEventAvro convertDeviceRemoved(DeviceRemovedEventProto proto) {
        return DeviceRemovedEventAvro.newBuilder()
                .setId(proto.getId())
                .build();
    }

    private ScenarioAddedEventAvro convertScenarioAdded(ScenarioAddedEventProto proto) {
        List<ScenarioConditionAvro> conditions = proto.getConditionsList().stream()
                .map(this::convertCondition)
                .toList();

        List<DeviceActionAvro> actions = proto.getActionsList().stream()
                .map(this::convertAction)
                .toList();

        return ScenarioAddedEventAvro.newBuilder()
                .setName(proto.getName())
                .setConditions(conditions)
                .setActions(actions)
                .build();
    }

    private ScenarioRemovedEventAvro convertScenarioRemoved(ScenarioRemovedEventProto proto) {
        return ScenarioRemovedEventAvro.newBuilder()
                .setName(proto.getName())
                .build();
    }

    private ScenarioConditionAvro convertCondition(ScenarioConditionProto proto) {
        ScenarioConditionAvro.Builder builder = ScenarioConditionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(ConditionTypeAvro.valueOf(proto.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(proto.getOperation().name()));

        switch (proto.getValueCase()) {
            case BOOL_VALUE -> builder.setValue(proto.getBoolValue());
            case INT_VALUE -> builder.setValue(proto.getIntValue());
            default -> builder.setValue(null);
        }

        return builder.build();
    }

    private DeviceActionAvro convertAction(DeviceActionProto proto) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(ActionTypeAvro.valueOf(proto.getType().name()))
                .setValue(proto.getValue())
                .build();
    }
}