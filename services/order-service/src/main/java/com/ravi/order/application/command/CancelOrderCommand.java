package com.ravi.order.application.command;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Command representing intent to cancel an active order.
 */
public record CancelOrderCommand(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        String reason
) {}
