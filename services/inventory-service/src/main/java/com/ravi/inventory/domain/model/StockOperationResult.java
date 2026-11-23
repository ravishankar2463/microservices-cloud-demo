package com.ravi.inventory.domain.model;

import java.io.Serializable;

public record StockOperationResult(
        boolean success,
        String productId,
        int requestedQuantity,
        int affectedQuantity,
        int availableBefore,
        int availableAfter,
        int reservedBefore,
        int reservedAfter,
        String reason
) implements Serializable {}
