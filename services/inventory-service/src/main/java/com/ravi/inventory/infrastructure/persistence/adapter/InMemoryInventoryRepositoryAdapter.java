package com.ravi.inventory.infrastructure.persistence.adapter;

import com.ravi.inventory.domain.model.InventoryItem;
import com.ravi.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryInventoryRepositoryAdapter implements InventoryRepository {

    private final Map<String, InventoryItem> store = new ConcurrentHashMap<>();

    public InMemoryInventoryRepositoryAdapter() {
        store.put("PROD-LAPTOP", new InventoryItem("PROD-LAPTOP", "Dev Laptop Pro", 50, 0));
        store.put("PROD-MOUSE", new InventoryItem("PROD-MOUSE", "Ergonomic Mouse", 150, 0));
        store.put("PROD-KEYBOARD", new InventoryItem("PROD-KEYBOARD", "Mechanical Keyboard", 80, 0));
        store.put("PROD-TECH-001", new InventoryItem("PROD-TECH-001", "Enterprise Cloud Server Node", 100, 0));
    }

    @Override
    public Optional<InventoryItem> findById(String productId) {
        return Optional.ofNullable(store.get(productId));
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        store.put(item.productId(), item);
        return item;
    }

    @Override
    public Map<String, InventoryItem> findAll() {
        return Collections.unmodifiableMap(store);
    }
}
