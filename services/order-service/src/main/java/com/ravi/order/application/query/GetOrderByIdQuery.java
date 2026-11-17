package com.ravi.order.application.query;

import java.util.UUID;

/**
 * Query DTO for retrieving an order projection by ID.
 */
public record GetOrderByIdQuery(UUID orderId) {}
