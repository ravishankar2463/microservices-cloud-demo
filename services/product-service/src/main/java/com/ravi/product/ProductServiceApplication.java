package com.ravi.product;

import com.ravi.product.domain.model.Category;
import com.ravi.product.domain.model.Product;
import com.ravi.product.infrastructure.persistence.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner seedProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.saveAll(List.of(
                    new Product("SKU-LAPTOP-001", "MacBook Pro M3", "High-performance laptop for developers", Category.ELECTRONICS, new BigDecimal("1999.00")),
                    new Product("SKU-PHONE-002", "iPhone 15 Pro", "Flagship smartphone with titanium design", Category.ELECTRONICS, new BigDecimal("999.00")),
                    new Product("SKU-HEADPHONE-003", "Sony WH-1000XM5", "Noise cancelling wireless headphones", Category.ACCESSORIES, new BigDecimal("349.99")),
                    new Product("SKU-KEYBOARD-004", "Mechanical Keyboard", "Wireless mechanical keyboard with RGB", Category.ACCESSORIES, new BigDecimal("129.50"))
                ));
            }
        };
    }
}
