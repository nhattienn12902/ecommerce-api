package com.nhattienn.ecommerce.product;

import com.nhattienn.ecommerce.product.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "active", target = "isActive")
    ProductResponse toResponse(Product product);
}