package ru.yandex.practicum.collector.service;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;

@Component
public class SensorProtoToAvroConverter {

    public SensorEventAvro convert(SensorEventProto event) {
        Object payload = extractPayload(event);

        Instant timestamp = Instant.ofEpochSecond(
                event.getTimestamp().getSeconds(),
                event.getTimestamp().getNanos()
        );

        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(timestamp)
                .setPayload(payload)
                .build();
    }

    private Object extractPayload(SensorEventProto event) {
        return switch (event.getPayloadCase()) {
            case MOTION_SENSOR -> convertMotion(event.getMotionSensor());
            case TEMPERATURE_SENSOR -> convertTemperature(event.getTemperatureSensor());
            case LIGHT_SENSOR -> convertLight(event.getLightSensor());
            case CLIMATE_SENSOR -> convertClimate(event.getClimateSensor());
            case SWITCH_SENSOR -> convertSwitch(event.getSwitchSensor());
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события: " + event.getPayloadCase());
        };
    }

    private MotionSensorAvro convertMotion(MotionSensorProto proto) {
        return MotionSensorAvro.newBuilder()
                .setLinkQuality(proto.getLinkQuality())
                .setMotion(proto.getMotion())
                .setVoltage(proto.getVoltage())
                .build();
    }

    private TemperatureSensorAvro convertTemperature(TemperatureSensorProto proto) {
        return TemperatureSensorAvro.newBuilder()
                .setTemperatureC(proto.getTemperatureC())
                .setTemperatureF(proto.getTemperatureF())
                .build();
    }

    private LightSensorAvro convertLight(LightSensorProto proto) {
        return LightSensorAvro.newBuilder()
                .setLinkQuality(proto.getLinkQuality())
                .setLuminosity(proto.getLuminosity())
                .build();
    }

    private ClimateSensorAvro convertClimate(ClimateSensorProto proto) {
        return ClimateSensorAvro.newBuilder()
                .setTemperatureC(proto.getTemperatureC())
                .setHumidity(proto.getHumidity())
                .setCo2Level(proto.getCo2Level())
                .build();
    }

    private SwitchSensorAvro convertSwitch(SwitchSensorProto proto) {
        return SwitchSensorAvro.newBuilder()
                .setState(proto.getState())
                .build();
    }
}