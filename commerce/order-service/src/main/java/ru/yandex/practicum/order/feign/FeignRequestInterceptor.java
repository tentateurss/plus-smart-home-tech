package ru.yandex.practicum.order.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public void apply(RequestTemplate template) {
        String requestId = MDC.get(REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        template.header(REQUEST_ID_HEADER, requestId);
    }
}