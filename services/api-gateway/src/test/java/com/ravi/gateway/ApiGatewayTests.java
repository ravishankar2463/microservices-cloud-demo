package com.ravi.gateway;

import com.ravi.gateway.presentation.rest.controller.GatewayRoutingController;
import com.ravi.gateway.infrastructure.filter.CorrelationIdFilter;
import com.ravi.gateway.infrastructure.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiGatewayTests {

    private MockMvc mockMvc;
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        GatewayRoutingController controller = new GatewayRoutingController(rateLimiterService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    @DisplayName("Gateway: CorrelationIdFilter injects X-Correlation-ID header into HTTP response")
    void testCorrelationIdFilter() throws Exception {
        mockMvc.perform(get("/api/routes"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    @DisplayName("Gateway: Rate limiter allows requests under quota and rejects excessive requests")
    void testRateLimiter() {
        String testIp = "192.168.1.100";
        rateLimiterService.reset(testIp);

        // Requests up to 100 must be allowed
        for (int i = 0; i < 100; i++) {
            assertThat(rateLimiterService.allowRequest(testIp)).isTrue();
        }

        // 101st request must be denied
        assertThat(rateLimiterService.allowRequest(testIp)).isFalse();
    }
}
