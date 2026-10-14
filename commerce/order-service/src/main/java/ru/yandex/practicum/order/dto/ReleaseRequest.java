package ru.yandex.practicum.order.dto;

public record ReleaseRequest(
        Long productId,
        Integer quantity
) {
}