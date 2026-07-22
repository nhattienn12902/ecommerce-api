package com.nhattienn.ecommerce.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(

        @NotBlank(message = "Category name must not be blank.")
        @Size(max = 150, message = "Category name must not exceed 150 characters.")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters.")
        String description
) {}