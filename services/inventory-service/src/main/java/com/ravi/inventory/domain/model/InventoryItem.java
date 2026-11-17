package com.ravi.inventory.domain.model;

import java.io.Serializable;

public record InventoryItem(
        String productId,
        String name,
        int availableQuantity,
        int reservedQuantity
) implements Serializable {}
