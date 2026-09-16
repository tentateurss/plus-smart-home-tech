package ru.yandex.practicum.collector.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.service.EventService;

@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class HubEventController {

    private final EventService eventService;

    @PostMapping("/hubs")
    @ResponseStatus(HttpStatus.OK)
    public void collectHubEvent(@Valid @RequestBody HubEvent event) {
        log.info("Получено событие хаба: type={}, hubId={}", event.getType(), event.getHubId());
        eventService.processHubEvent(event);
    }
}