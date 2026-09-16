package ru.yandex.practicum.collector.service;

import org.springframework.stereotype.Component;
import ru.yandex.practicum.collector.model.sensor.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

@Component
public class SensorEventConverter {

    public SensorEventAvro convertToAvro(SensorEvent event) {
        Object payload = extractPayload(event);

        return SensorEventAvro.newBuilder()
                .setId(event.getId())
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp())
                .setPayload(payload)
                .build();
    }

    private Object extractPayload(SensorEvent event) {
        if (event instanceof LightSensorEvent light) {
            return LightSensorAvro.newBuilder()
                    .setLinkQuality(light.getLinkQuality())
                    .setLuminosity(light.getLuminosity())
                    .build();
        }

        if (event instanceof SwitchSensorEvent switchEvent) {
            return SwitchSensorAvro.newBuilder()
                    .setState(switchEvent.isState())
                    .build();
        }

        if (event instanceof ClimateSensorEvent climate) {
            return ClimateSensorAvro.newBuilder()
                    .setTemperatureC(climate.getTemperatureC())
                    .setHumidity(climate.getHumidity())
                    .setCo2Level(climate.getCo2Level())
                    .build();
        }

        if (event instanceof MotionSensorEvent motion) {
            return MotionSensorAvro.newBuilder()
                    .setLinkQuality(motion.getLinkQuality())
                    .setMotion(motion.isMotion())
                    .setVoltage(motion.getVoltage())
                    .build();
        }

        if (event instanceof TemperatureSensorEvent temperature) {
            return TemperatureSensorAvro.newBuilder()
                    .setTemperatureC(temperature.getTemperatureC())
                    .setTemperatureF(temperature.getTemperatureF())
                    .build();
        }

        throw new IllegalArgumentException("Неизвестный тип события сенсора: " + event.getClass());
    }
}