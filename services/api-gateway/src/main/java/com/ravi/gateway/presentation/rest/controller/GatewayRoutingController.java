package com.ravi.gateway.presentation.rest.controller;

import com.ravi.gateway.infrastructure.filter.CorrelationIdFilter;
import com.ravi.gateway.infrastructure.ratelimit.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@Tag(name = "Gateway Edge Routing", description = "Edge reverse proxy routes, Token-Bucket rate limiting, and Keycloak JWT inspection")
public class GatewayRoutingController {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutingController.class);

    private final RateLimiterService rateLimiterService;
    private final RestClient restClient;

    @Value("${order.service.url:http://localhost:8081}")
    private String orderServiceUrl = "http://localhost:8081";

    @Value("${inventory.service.url:http://localhost:8082}")
    private String inventoryServiceUrl = "http://localhost:8082";

    @Value("${product.service.url:http://localhost:8085}")
    private String productServiceUrl = "http://localhost:8085";

    @Value("${payment.service.url:http://localhost:8084}")
    private String paymentServiceUrl = "http://localhost:8084";

    @Value("${notification.service.url:http://localhost:8083}")
    private String notificationServiceUrl = "http://localhost:8083";

    public GatewayRoutingController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping("/routes")
    @Operation(summary = "Get configured routing table", description = "Returns the active backend service URLs mapped to edge endpoints.")
    public ResponseEntity<Map<String, String>> getRoutes() {
        return ResponseEntity.ok(Map.of(
                "orderService", orderServiceUrl,
                "inventoryService", inventoryServiceUrl,
                "productService", productServiceUrl,
                "paymentService", paymentServiceUrl,
                "notificationService", notificationServiceUrl
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user context", description = "Extracts username and roles from the Keycloak JWT token passed in the Authorization header.")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authenticated", false, "message", "No active JWT token provided"));
        }

        var roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", authentication.getName(),
                "roles", roles
        ));
    }

    @GetMapping("/products/**")
    public ResponseEntity<String> routeProducts(HttpServletRequest request) {
        return forwardRequest(productServiceUrl, request);
    }

    @RequestMapping(value = "/orders/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> routeOrders(HttpServletRequest request) {
        return forwardRequest(orderServiceUrl, request);
    }

    @RequestMapping(value = "/inventory/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> routeInventory(HttpServletRequest request) {
        return forwardRequest(inventoryServiceUrl, request);
    }

    @RequestMapping(value = "/notifications/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<String> routeNotifications(HttpServletRequest request) {
        return forwardRequest(notificationServiceUrl, request);
    }

    @RequestMapping(value = "/payments/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> routePayments(HttpServletRequest request) {
        return forwardRequest(paymentServiceUrl, request);
    }

    private ResponseEntity<String> forwardRequest(String targetBaseUrl, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        if (!rateLimiterService.tryAcquire(clientIp)) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\":\"Rate limit exceeded. Try again later.\"}");
        }

        String path = request.getRequestURI();
        String correlationId = request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        try {
            org.springframework.http.HttpMethod method = org.springframework.http.HttpMethod.valueOf(request.getMethod());
            String fullUri = targetBaseUrl + path + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

            var reqSpec = restClient.method(method)
                    .uri(fullUri)
                    .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId != null ? correlationId : "");

            if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
                String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                if (!body.isBlank()) {
                    reqSpec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(body);
                }
            }

            String response = reqSpec.retrieve().body(String.class);
            return ResponseEntity.ok(response != null ? response : "");
        } catch (Exception e) {
            log.error("Failed to forward request to {}: {}", targetBaseUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Downstream service unavailable: " + e.getMessage() + "\"}");
        }
    }
}
