package com.ravi.product.presentation.rest.controller;

import com.ravi.product.application.service.ProductService;
import com.ravi.product.domain.model.Category;
import com.ravi.product.presentation.rest.request.CreateProductRequest;
import com.ravi.product.presentation.rest.response.ProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Product Catalog", description = "Endpoints for discovering, creating, and managing catalog items and categories")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(
            summary = "List catalog products",
            description = "Retrieves active products, with optional filtering by product category and text substring search on name or description."
    )
    @ApiResponse(
            responseCode = "200",
            description = "List of matched products",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ProductDto.class)))
    )
    public ResponseEntity<List<ProductDto>> listProducts(
            @Parameter(description = "Filter by product category (e.g. ELECTRONICS, CLOTHING, BOOKS, ACCESSORIES, HOME, SPORTS)")
            @RequestParam(required = false) Category category,
            @Parameter(description = "Search query matched case-insensitively against name and description", example = "keyboard")
            @RequestParam(required = false) String search) {
        List<ProductDto> products = productService.getProducts(category, search);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by database ID", description = "Fetches a single product by its primary key ID.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ProductDto> getProductById(
            @Parameter(description = "Primary key database ID of the product", example = "1")
            @PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU", description = "Fetches a product using its unique stock-keeping unit (SKU).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Product found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product with given SKU not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ProductDto> getProductBySku(
            @Parameter(description = "Unique SKU code", example = "PROD-TECH-001")
            @PathVariable String sku) {
        return productService.getProductBySku(sku)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(
            summary = "Create a new catalog product",
            description = "Creates a new product with given SKU, name, price, and category. SKU must be unique across the catalog."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Product created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure or duplicate SKU conflict",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductDto created = productService.createProduct(request);
        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a product", description = "Soft-deactivates an existing product by ID, removing it from active catalog search listings.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deactivated successfully"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Product not found",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<Void> deactivateProduct(
            @Parameter(description = "Primary key database ID of the product to deactivate", example = "1")
            @PathVariable Long id) {
        boolean removed = productService.deactivateProduct(id);
        if (removed) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
