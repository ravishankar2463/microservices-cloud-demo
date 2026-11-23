package com.ravi.order.infrastructure.persistence.config;

import com.ravi.order.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Ensures PostgreSQL's Hibernate-generated orders_status_check constraint stays aligned
 * with all OrderStatus enum values (including COMPLETED) across rolling upgrades.
 */
@Component
public class OrderStatusConstraintMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusConstraintMigration.class);

    private final DataSource dataSource;

    public OrderStatusConstraintMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("postgresql")) {
                return;
            }
            String allowedValues = Arrays.stream(OrderStatus.values())
                    .map(s -> "'" + s.name() + "'")
                    .collect(Collectors.joining(", "));

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
                stmt.execute("ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (" + allowedValues + "))");
                log.info("Synchronized PostgreSQL orders_status_check constraint with OrderStatus values: [{}]", allowedValues);
            }
        } catch (Exception e) {
            log.warn("Could not synchronize orders_status_check constraint: {}", e.getMessage());
        }
    }
}
