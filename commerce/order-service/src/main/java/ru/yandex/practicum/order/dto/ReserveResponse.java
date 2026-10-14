package ru.yandex.practicum.order.dto;

public record ReserveResponse(
        boolean success,
        Integer availableQuantity,
        String message
) {
}