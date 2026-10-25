package ru.yandex.practicum.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false",

        "app.security.users[0].username=ivan",
        "app.security.users[0].password=ivan",
        "app.security.users[0].roles[0]=USER",
        "app.security.users[1].username=anna",
        "app.security.users[1].password=anna",
        "app.security.users[1].roles[0]=USER",
        "app.security.users[1].roles[1]=ADMIN"
})
class GatewaySecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void catalogGet_isPublic() {
        webTestClient.get().uri("http://localhost/api/products")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void orderCreate_withoutCredentials_isUnauthorized() {
        webTestClient.post().uri("http://localhost/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void corsPreflight_isPublic() {
        webTestClient.options().uri("http://localhost/api/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void orderCreate_withUserCredentials_passesSecurity() {
        webTestClient.post().uri("http://localhost/api/orders")
                .header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void productWrite_withUserCredentials_isForbidden() {
        webTestClient.post().uri("http://localhost/api/products")
                .header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void orderList_withUserCredentials_isForbidden() {
        webTestClient.get().uri("http://localhost/api/orders")
                .header(HttpHeaders.AUTHORIZATION, basic("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void productWrite_withAdminCredentials_passesSecurity() {
        webTestClient.post().uri("http://localhost/api/products")
                .header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void orderList_withAdminCredentials_passesSecurity() {
        webTestClient.get().uri("http://localhost/api/orders")
                .header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unknownRoute_withAdminCredentials_isForbidden() {
        webTestClient.get().uri("http://localhost/api/unknown-route")
                .header(HttpHeaders.AUTHORIZATION, basic("anna", "anna"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private String basic(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration
    static class TestBackendConfig {
        @Bean
        RouterFunction<ServerResponse> testBackendRoutes() {
            return route(path("/**"), request -> ServerResponse.ok().build());
        }
    }
}