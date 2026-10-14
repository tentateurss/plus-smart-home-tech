package ru.yandex.practicum.order.service;

import java.util.function.Function;

public sealed interface ServiceCallResult<T>
        permits ServiceCallResult.Success, ServiceCallResult.Failure, ServiceCallResult.Degraded {

    record Success<T>(T value) implements ServiceCallResult<T> {
    }

    record Failure<T>(String reason) implements ServiceCallResult<T> {
    }

    record Degraded<T>(String reason) implements ServiceCallResult<T> {
    }

    default <R> ServiceCallResult<R> map(Function<T, R> mapper) {
        return switch (this) {
            case Success<T> s -> new Success<>(mapper.apply(s.value()));
            case Failure<T> f -> new Failure<>(f.reason());
            case Degraded<T> d -> new Degraded<>(d.reason());
        };
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    default boolean isDegraded() {
        return this instanceof Degraded<T>;
    }

    default T getValue() {
        if (this instanceof Success<T> s) {
            return s.value();
        }
        throw new IllegalStateException("Not a success result");
    }
}