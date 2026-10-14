package ru.yandex.practicum.order.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(Long productId, Throwable cause) {
        super("Сервис склада недоступен для товара productId=" + productId, cause);
    }
}