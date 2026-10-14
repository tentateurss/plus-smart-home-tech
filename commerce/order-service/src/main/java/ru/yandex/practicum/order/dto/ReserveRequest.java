package ru.yandex.practicum.order.dto;

public record ReserveRequest(
        Long productId,
        Integer quantity
) {
}