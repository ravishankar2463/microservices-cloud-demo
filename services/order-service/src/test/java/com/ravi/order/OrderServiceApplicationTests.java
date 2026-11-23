package com.ravi.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void shouldLoadMainApplicationYamlWithoutDuplicateKeys() {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        assertDoesNotThrow(() -> {
            loader.load("main-yaml", new FileSystemResource("src/main/resources/application.yaml"));
        }, "src/main/resources/application.yaml must not contain duplicate keys or syntax errors");
    }
}

