package ru.yandex.practicum.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.product.dto.*;
import ru.yandex.practicum.product.entity.Category;
import ru.yandex.practicum.product.entity.Product;
import ru.yandex.practicum.product.exception.NotFoundException;
import ru.yandex.practicum.product.repository.CategoryRepository;
import ru.yandex.practicum.product.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toCategoryDto).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDto getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + id + " не найдена"));
        return toCategoryDto(category);
    }

    @Transactional
    public CategoryDto createCategory(CreateCategoryRequest request) {
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return toCategoryDto(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getAllActiveProducts() {
        return productRepository.findAllByActiveTrue().stream().map(this::toProductDto).toList();
    }

    @Transactional(readOnly = true)
    public ProductDto getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));
        return toProductDto(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByCategory(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new NotFoundException("Категория с id=" + categoryId + " не найдена");
        }
        return productRepository.findAllByCategoryIdAndActiveTrue(categoryId).stream()
                .map(this::toProductDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDto> searchProducts(String query) {
        return productRepository.searchByName(query).stream().map(this::toProductDto).toList();
    }

    @Transactional
    public ProductDto createProduct(CreateProductRequest request) {
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + request.categoryId() + " не найдена"));
        }

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .category(category)
                .imageUrl(request.imageUrl())
                .active(true)
                .build();

        return toProductDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new NotFoundException("Категория с id=" + request.categoryId() + " не найдена"));
            product.setCategory(category);
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return toProductDto(productRepository.save(product));
    }

    private CategoryDto toCategoryDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getDescription());
    }

    private ProductDto toProductDto(Product product) {
        CategoryDto categoryDto = product.getCategory() == null ? null : toCategoryDto(product.getCategory());
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                categoryDto,
                product.getImageUrl(),
                product.getActive()
        );
    }
}