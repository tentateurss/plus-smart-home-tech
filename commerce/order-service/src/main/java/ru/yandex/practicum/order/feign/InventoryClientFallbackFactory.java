package ru.yandex.practicum.order.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.dto.ReleaseRequest;
import ru.yandex.practicum.order.dto.ReserveRequest;
import ru.yandex.practicum.order.dto.ReserveResponse;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;

@Slf4j
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    @Override
    public InventoryClient create(Throwable cause) {
        log.warn("Сервис склада недоступен: {}", cause.getMessage(), cause);

        return new InventoryClient() {
            @Override
            public ReserveResponse reserveStock(ReserveRequest request) {
                throw new InventoryServiceUnavailableException(request.productId(), cause);
            }

            @Override
            public ReserveResponse releaseStock(ReleaseRequest request) {
                throw new InventoryServiceUnavailableException(request.productId(), cause);
            }
        };
    }
}