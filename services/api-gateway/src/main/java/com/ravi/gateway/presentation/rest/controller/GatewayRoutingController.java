package com.ravi.gateway.presentation.rest.controller;

import com.ravi.gateway.infrastructure.filter.CorrelationIdFilter;
import com.ravi.gateway.infrastructure.ratelimit.RateLimiterService;
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

    public GatewayRoutingController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, String>> getRoutes() {
        return ResponseEntity.ok(Map.of(
                "orderService", orderServiceUrl,
                "inventoryService", inventoryServiceUrl,
                "productService", productServiceUrl,
                "paymentService", paymentServiceUrl
        ));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()),
                "authenticated", authentication.isAuthenticated()
        ));
    }

    @GetMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback(@RequestParam(defaultValue = "Service Unavailable") String reason) {
        log.warn("Gateway circuit-breaker fallback triggered: {}", reason);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service temporarily unavailable. Please retry shortly.",
                "reason", reason,
                "status", 503
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
            String response = restClient.get()
                    .uri(targetBaseUrl + path)
                    .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId != null ? correlationId : "")
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to forward request to {}: {}", targetBaseUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Downstream service unavailable: " + e.getMessage() + "\"}");
        }
    }
}
