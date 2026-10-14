package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.dto.*;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
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
        for (Long productId : quantityByProductId.keySet()) {
            try {
                ProductDto product = productClient.getProductById(productId);
                if (product == null) {
                    throw new OrderProcessingException("Товар с id=" + productId + " не найден");
                }
                if (!Boolean.TRUE.equals(product.active())) {
                    throw new OrderProcessingException("Товар с id=" + productId + " снят с продажи");
                }
                products.put(productId, product);
            } catch (FeignException.NotFound e) {
                throw new OrderProcessingException("Товар с id=" + productId + " не найден");
            } catch (OrderProcessingException e) {
                throw e;
            } catch (FeignException e) {
                log.error("Ошибка вызова product-service для productId={}", productId, e);
                throw new OrderProcessingException("Не удалось получить данные товара id=" + productId);
            }
        }

        List<ReleaseRequest> reservations = new ArrayList<>();

        try {
            for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
                Long productId = entry.getKey();
                Integer quantity = entry.getValue();

                try {
                    ReserveResponse response = inventoryClient.reserveStock(
                            new ReserveRequest(productId, quantity));
                    if (!response.success()) {
                        throw new OrderProcessingException(
                                "Не удалось зарезервировать товар id=" + productId + ": " + response.message());
                    }
                    reservations.add(new ReleaseRequest(productId, quantity));
                } catch (FeignException.NotFound e) {
                    throw new OrderProcessingException("Складская запись для товара id=" + productId + " не найдена");
                } catch (FeignException.Conflict e) {
                    throw new OrderProcessingException("Недостаточно товара id=" + productId + " на складе");
                } catch (OrderProcessingException e) {
                    throw e;
                } catch (FeignException e) {
                    log.error("Ошибка вызова inventory-service для productId={}", productId, e);
                    throw new OrderProcessingException("Не удалось зарезервировать товар id=" + productId);
                }
            }
        } catch (RuntimeException e) {
            compensateReservations(reservations);
            throw e;
        }

        Order order = Order.builder()
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .status(STATUS_CONFIRMED)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.items()) {
            ProductDto product = products.get(itemRequest.productId());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(product.id())
                    .productName(product.name())
                    .quantity(itemRequest.quantity())
                    .price(product.price())
                    .build();
            order.getItems().add(item);

            BigDecimal itemTotal = product.price()
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()));
            totalPrice = totalPrice.add(itemTotal);
        }

        order.setTotalPrice(totalPrice);

        Order saved = orderRepository.save(order);
        log.info("Заказ id={} создан, сумма={}", saved.getId(), saved.getTotalPrice());
        return toDto(saved);
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