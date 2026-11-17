package com.ravi.product.application.service;

import com.ravi.product.domain.model.Category;
import com.ravi.product.domain.model.Product;
import com.ravi.product.infrastructure.persistence.repository.ProductRepository;
import com.ravi.product.presentation.rest.request.CreateProductRequest;
import com.ravi.product.presentation.rest.response.ProductDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductDto> getProducts(Category category, String search) {
        List<Product> products;
        if (category != null) {
            products = productRepository.findByCategoryAndActiveTrue(category);
        } else if (search != null && !search.isBlank()) {
            products = productRepository.findByNameContainingIgnoreCaseAndActiveTrue(search.trim());
        } else {
            products = productRepository.findByActiveTrue();
        }
        return products.stream().map(ProductDto::fromEntity).toList();
    }

    public Optional<ProductDto> getProductById(Long id) {
        return productRepository.findById(id).map(ProductDto::fromEntity);
    }

    public Optional<ProductDto> getProductBySku(String sku) {
        return productRepository.findBySku(sku).map(ProductDto::fromEntity);
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new IllegalArgumentException("Product with SKU '" + request.sku() + "' already exists");
        }
        Product product = new Product(
            request.sku(),
            request.name(),
            request.description(),
            request.category(),
            request.price()
        );
        Product saved = productRepository.save(product);
        return ProductDto.fromEntity(saved);
    }

    @Transactional
    public boolean deactivateProduct(Long id) {
        return productRepository.findById(id).map(product -> {
            product.setActive(false);
            productRepository.save(product);
            return true;
        }).orElse(false);
    }
}
