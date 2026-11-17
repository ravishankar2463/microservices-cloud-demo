package com.ravi.product.integration;

import com.ravi.product.application.service.ProductService;
import com.ravi.product.domain.model.Category;
import com.ravi.product.infrastructure.persistence.repository.ProductRepository;
import com.ravi.product.presentation.rest.controller.ProductController;
import com.ravi.product.presentation.rest.request.CreateProductRequest;
import com.ravi.product.presentation.rest.response.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productController = new ProductController(productService);
    }

    @Test
    @DisplayName("Should create product and retrieve by SKU")
    void shouldCreateAndRetrieveProductBySku() {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-TEST-001",
            "Test Mechanical Keyboard",
            "Ergonomic keyboard",
            Category.ACCESSORIES,
            new BigDecimal("149.99")
        );

        ProductDto created = productService.createProduct(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.sku()).isEqualTo("SKU-TEST-001");
        assertThat(created.price()).isEqualByComparingTo(new BigDecimal("149.99"));

        Optional<ProductDto> fetched = productService.getProductBySku("SKU-TEST-001");
        assertThat(fetched).isPresent();
        assertThat(fetched.get().name()).isEqualTo("Test Mechanical Keyboard");
    }

    @Test
    @DisplayName("Should filter products by category")
    void shouldFilterProductsByCategory() {
        productService.createProduct(new CreateProductRequest(
            "SKU-ELEC-1", "Gaming Laptop", null, Category.ELECTRONICS, new BigDecimal("2500.00")
        ));
        productService.createProduct(new CreateProductRequest(
            "SKU-BOOK-1", "Clean Code Book", null, Category.BOOKS, new BigDecimal("45.00")
        ));

        List<ProductDto> electronics = productService.getProducts(Category.ELECTRONICS, null);
        assertThat(electronics).hasSize(1);
        assertThat(electronics.get(0).sku()).isEqualTo("SKU-ELEC-1");

        List<ProductDto> books = productService.getProducts(Category.BOOKS, null);
        assertThat(books).hasSize(1);
        assertThat(books.get(0).sku()).isEqualTo("SKU-BOOK-1");
    }

    @Test
    @DisplayName("Should reject duplicate SKU with IllegalArgumentException")
    void shouldRejectDuplicateSku() {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-DUP-1", "Duplicate Item", null, Category.HOME, new BigDecimal("19.99")
        );
        productService.createProduct(request);

        assertThatThrownBy(() -> productService.createProduct(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should soft delete / deactivate product")
    void shouldDeactivateProduct() {
        ProductDto created = productService.createProduct(new CreateProductRequest(
            "SKU-DEL-1", "Temporary Item", null, Category.SPORTS, new BigDecimal("89.00")
        ));

        boolean deactivated = productService.deactivateProduct(created.id());
        assertThat(deactivated).isTrue();

        List<ProductDto> activeProducts = productService.getProducts(null, null);
        assertThat(activeProducts).noneMatch(p -> p.sku().equals("SKU-DEL-1"));
    }

    @Test
    @DisplayName("Should test controller REST endpoints")
    void shouldTestControllerEndpoints() {
        CreateProductRequest request = new CreateProductRequest(
            "SKU-CTRL-1", "Controller Tested Item", null, Category.ELECTRONICS, new BigDecimal("99.95")
        );

        ResponseEntity<ProductDto> createResponse = productController.createProduct(request);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = createResponse.getBody().id();

        ResponseEntity<ProductDto> getResponse = productController.getProductById(id);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().sku()).isEqualTo("SKU-CTRL-1");

        ResponseEntity<ProductDto> getSkuResponse = productController.getProductBySku("SKU-CTRL-1");
        assertThat(getSkuResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
