package com.ravi.inventory.domain.repository;

import com.ravi.inventory.domain.model.InventoryItem;

import java.util.Map;
import java.util.Optional;

public interface InventoryRepository {
    Optional<InventoryItem> findById(String productId);
    InventoryItem save(InventoryItem item);
    Map<String, InventoryItem> findAll();
}
