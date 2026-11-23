package com.ravi.gateway.presentation.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GatewaySwaggerAggregationControllerTest {

    private MockMvc mockMvc;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        GatewaySwaggerAggregationController controller = new GatewaySwaggerAggregationController(restClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Swagger Aggregator: returns 404 for unknown downstream service")
    void testUnknownServiceReturns404() throws Exception {
        mockMvc.perform(get("/services/unknown-service/openapi.json"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unknown service name: unknown-service"));
    }

    @Test
    @DisplayName("Swagger Aggregator: successfully proxies OpenAPI spec for order-service")
    void testProxiesOrderServiceApiDocs() throws Exception {
        String mockSpec = "{\"openapi\":\"3.0.1\",\"info\":{\"title\":\"Order Service API\"}}";

        mockServer.expect(requestTo("http://localhost:8081/v3/api-docs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockSpec, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/services/order-service/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("Order Service API"));

        mockServer.verify();
    }

    @Test
    @DisplayName("Swagger Aggregator: returns 503 when downstream service is unreachable")
    void testDownstreamServiceUnavailableReturns503() throws Exception {
        mockServer.expect(requestTo("http://localhost:8082/v3/api-docs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(get("/services/inventory-service/openapi.json"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());

        mockServer.verify();
    }
}
