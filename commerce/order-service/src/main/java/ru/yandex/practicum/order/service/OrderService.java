package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.dto.*;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.feign.InventoryClient;
import ru.yandex.practicum.order.feign.ProductClient;
import ru.yandex.practicum.order.feign.ProductDto;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_PENDING = "PENDING_CONFIRMATION";

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long id) {
        Order order = orderRepository.findByIdWithItems(id);
        if (order == null) {
            throw new NotFoundException("Заказ с id=" + id + " не найден");
        }
        return toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getAllOrders() {
        return orderRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getOrdersByEmail(String email) {
        return orderRepository.findByCustomerEmailWithItems(email).stream().map(this::toDto).toList();
    }

    public OrderDto createOrder(CreateOrderRequest request) {
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantityByProductId.merge(item.productId(), item.quantity(), Integer::sum);
        }

        Map<Long, ProductDto> products = new HashMap<>();
        boolean productDegraded = false;
        String productDegradedReason = null;

        for (Long productId : quantityByProductId.keySet()) {
            ServiceCallResult<ProductDto> result = callProductService(productId);

            switch (result) {
                case ServiceCallResult.Success<ProductDto> s -> products.put(productId, s.value());
                case ServiceCallResult.Failure<ProductDto> f -> {
                    throw new OrderProcessingException(f.reason());
                }
                case ServiceCallResult.Degraded<ProductDto> d -> {
                    productDegraded = true;
                    productDegradedReason = d.reason();
                }
            }
        }

        List<ReleaseRequest> reservations = new ArrayList<>();
        boolean inventoryDegraded = false;
        String inventoryDegradedReason = null;

        try {
            for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
                Long productId = entry.getKey();
                Integer quantity = entry.getValue();

                ServiceCallResult<ReserveResponse> result = callInventoryReserve(productId, quantity);

                switch (result) {
                    case ServiceCallResult.Success<ReserveResponse> s -> {
                        reservations.add(new ReleaseRequest(productId, quantity));
                    }
                    case ServiceCallResult.Failure<ReserveResponse> f -> {
                        throw new OrderProcessingException(f.reason());
                    }
                    case ServiceCallResult.Degraded<ReserveResponse> d -> {
                        inventoryDegraded = true;
                        inventoryDegradedReason = d.reason();
                    }
                }
            }
        } catch (RuntimeException e) {
            compensateReservations(reservations);
            throw e;
        }

        boolean degraded = productDegraded || inventoryDegraded;
        String status = degraded ? STATUS_PENDING : STATUS_CONFIRMED;
        String statusDetails = buildStatusDetails(productDegraded, productDegradedReason,
                inventoryDegraded, inventoryDegradedReason);

        Order order = Order.builder()
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .status(status)
                .statusDetails(statusDetails)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            ProductDto product = products.get(itemRequest.productId());

            String productName;
            BigDecimal price;

            if (product != null) {
                productName = product.name();
                price = product.price();
            } else {
                productName = "Товар #" + itemRequest.productId() + " (ожидает проверки)";
                price = BigDecimal.ZERO;
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(itemRequest.productId())
                    .productName(productName)
                    .quantity(itemRequest.quantity())
                    .price(price)
                    .build();
            order.getItems().add(item);

            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(itemRequest.quantity()));
            totalPrice = totalPrice.add(itemTotal);
        }

        order.setTotalPrice(totalPrice);

        Order saved = orderRepository.save(order);
        log.info("Заказ id={} создан, статус={}, сумма={}",
                saved.getId(), saved.getStatus(), saved.getTotalPrice());
        return toDto(saved);
    }

    private ServiceCallResult<ProductDto> callProductService(Long productId) {
        try {
            ProductDto product = productClient.getProductById(productId);

            if (product == null) {
                return new ServiceCallResult.Failure<>("Товар с id=" + productId + " не найден");
            }
            if (!Boolean.TRUE.equals(product.active())) {
                return new ServiceCallResult.Failure<>("Товар с id=" + productId + " снят с продажи");
            }
            return new ServiceCallResult.Success<>(product);

        } catch (ProductServiceUnavailableException e) {
            log.warn("Product-service недоступен для productId={}: {}", productId, e.getMessage());
            return new ServiceCallResult.Degraded<>(
                    "Сервис каталога недоступен: " + e.getCause().getMessage());

        } catch (FeignException.NotFound e) {
            return new ServiceCallResult.Failure<>("Товар с id=" + productId + " не найден");

        } catch (FeignException e) {
            log.error("Ошибка вызова product-service для productId={}", productId, e);
            return new ServiceCallResult.Degraded<>("Ошибка вызова сервиса каталога: " + e.getMessage());
        }
    }

    private ServiceCallResult<ReserveResponse> callInventoryReserve(Long productId, Integer quantity) {
        try {
            ReserveResponse response = inventoryClient.reserveStock(
                    new ReserveRequest(productId, quantity));

            if (!response.success()) {
                return new ServiceCallResult.Failure<>(
                        "Не удалось зарезервировать товар id=" + productId + ": " + response.message());
            }
            return new ServiceCallResult.Success<>(response);

        } catch (InventoryServiceUnavailableException e) {
            log.warn("Inventory-service недоступен для productId={}: {}", productId, e.getMessage());
            return new ServiceCallResult.Degraded<>(
                    "Сервис склада недоступен: " + e.getCause().getMessage());

        } catch (FeignException.NotFound e) {
            return new ServiceCallResult.Failure<>("Складская запись для товара id=" + productId + " не найдена");

        } catch (FeignException.Conflict e) {
            return new ServiceCallResult.Failure<>("Недостаточно товара id=" + productId + " на складе");

        } catch (FeignException e) {
            log.error("Ошибка вызова inventory-service для productId={}", productId, e);
            return new ServiceCallResult.Degraded<>("Ошибка вызова сервиса склада: " + e.getMessage());
        }
    }

    private void compensateReservations(List<ReleaseRequest> reservations) {
        for (ReleaseRequest release : reservations) {
            try {
                inventoryClient.releaseStock(release);
                log.info("Резерв снят: productId={}, quantity={}", release.productId(), release.quantity());
            } catch (Exception e) {
                log.error("Ошибка снятия резерва productId={}, quantity={}",
                        release.productId(), release.quantity(), e);
            }
        }
    }

    private String buildStatusDetails(boolean productDegraded, String productReason,
                                      boolean inventoryDegraded, String inventoryReason) {
        if (!productDegraded && !inventoryDegraded) {
            return null;
        }

        List<String> details = new ArrayList<>();
        if (productDegraded) {
            details.add("Каталог: " + productReason);
        }
        if (inventoryDegraded) {
            details.add("Склад: " + inventoryReason);
        }
        return "Заказ требует ручной проверки. " + String.join("; ", details);
    }

    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .toList();

        return new OrderDto(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTotalPrice(),
                order.getStatusDetails(),
                order.getCreatedAt(),
                itemDtos
        );
    }
}