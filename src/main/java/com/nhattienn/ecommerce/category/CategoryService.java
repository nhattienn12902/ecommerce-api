package com.nhattienn.ecommerce.category;

import com.nhattienn.ecommerce.category.dto.CategoryResponse;
import com.nhattienn.ecommerce.category.dto.CreateCategoryRequest;
import com.nhattienn.ecommerce.category.dto.UpdateCategoryRequest;
import com.nhattienn.ecommerce.common.exception.DuplicateResourceException;
import com.nhattienn.ecommerce.common.exception.BusinessException;
import com.nhattienn.ecommerce.common.exception.ResourceNotFoundException;
import com.nhattienn.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Category name already exists: " + request.name());
        }
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new DuplicateResourceException("Category name already exists: " + request.name());
        }

        category.setName(request.name());
        category.setDescription(request.description());

        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException("CATEGORY_IN_USE",
                    "Cannot delete category because it still has products referencing it.");
        }
        categoryRepository.deleteById(id);
    }
}