package com.nhattienn.ecommerce;

import com.nhattienn.ecommerce.category.Category;
import com.nhattienn.ecommerce.category.CategoryRepository;
import com.nhattienn.ecommerce.common.exception.ResourceNotFoundException;
import com.nhattienn.ecommerce.product.Product;
import com.nhattienn.ecommerce.product.ProductMapper;
import com.nhattienn.ecommerce.product.ProductRepository;
import com.nhattienn.ecommerce.product.ProductService;
import com.nhattienn.ecommerce.product.dto.CreateProductRequest;
import com.nhattienn.ecommerce.product.dto.ProductResponse;
import com.nhattienn.ecommerce.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    private Category electronics;
    private Product iphone;
    private ProductResponse iphoneResponse;

    @BeforeEach
    void setUp() {
        electronics = Category.builder()
                .id(1L)
                .name("Electronics")
                .description("Consumer electronics")
                .build();

        iphone = Product.builder()
                .id(1L)
                .name("Apple iPhone 15 Pro")
                .description("6.1-inch display, A17 Pro chip")
                .price(new BigDecimal("999.99"))
                .category(electronics)
                .build();

        iphoneResponse = new ProductResponse(
                1L, "Apple iPhone 15 Pro", "6.1-inch display, A17 Pro chip",
                new BigDecimal("999.99"), 1L, "Electronics", true, null, null);
    }

    // -----------------------------------------------------------------------
    // findAll()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all active products when no filter is applied")
        void shouldReturnAllActiveProducts() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Product> productPage = new PageImpl<>(List.of(iphone));

            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(productPage);
            when(productMapper.toResponse(iphone)).thenReturn(iphoneResponse);

            Page<ProductResponse> result = productService.findAll(null, null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Apple iPhone 15 Pro");
        }

        @Test
        @DisplayName("should return only matching products when keyword filter is applied")
        void shouldReturnFilteredProductsByKeyword() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Product> productPage = new PageImpl<>(List.of(iphone));

            when(productRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(productPage);
            when(productMapper.toResponse(iphone)).thenReturn(iphoneResponse);

            Page<ProductResponse> result = productService.findAll(null, "iphone", null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Apple iPhone 15 Pro");
        }
    }

    // -----------------------------------------------------------------------
    // findById()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return product when product exists and is active")
        void shouldReturnProductWhenActiveAndExists() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(iphone));
            when(productMapper.toResponse(iphone)).thenReturn(iphoneResponse);

            ProductResponse result = productService.findById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Apple iPhone 15 Pro");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.findById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product is soft-deleted")
        void shouldThrowWhenProductIsInactive() {
            // isActive mặc định là true trong @Builder.Default
            // cần tạo product với isActive = false
            Product inactiveProduct = Product.builder()
                    .id(2L)
                    .name("Deleted Product")
                    .price(new BigDecimal("99.99"))
                    .category(electronics)
                    .build();
            inactiveProduct.setActive(false);

            when(productRepository.findById(2L)).thenReturn(Optional.of(inactiveProduct));

            assertThatThrownBy(() -> productService.findById(2L))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Soft-deleted product không được map sang response
            verify(productMapper, never()).toResponse(any());
        }
    }

    // -----------------------------------------------------------------------
    // create()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create product successfully when category exists")
        void shouldCreateProductSuccessfully() {
            CreateProductRequest request = new CreateProductRequest(
                    "Apple iPhone 15 Pro", "6.1-inch display", new BigDecimal("999.99"), 1L);

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
            when(productRepository.save(any(Product.class))).thenReturn(iphone);
            when(productMapper.toResponse(iphone)).thenReturn(iphoneResponse);

            ProductResponse result = productService.create(request);

            assertThat(result.name()).isEqualTo("Apple iPhone 15 Pro");
            assertThat(result.categoryName()).isEqualTo("Electronics");

            // create() phải gọi save() — không có save thì product không được persist
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when category does not exist")
        void shouldThrowWhenCategoryNotFound() {
            CreateProductRequest request = new CreateProductRequest(
                    "Some Product", "desc", new BigDecimal("99.99"), 99L);

            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            verify(productRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // update()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update product successfully when product is active and category exists")
        void shouldUpdateProductSuccessfully() {
            UpdateProductRequest request = new UpdateProductRequest(
                    "iPhone 15 Pro Updated", "New description", new BigDecimal("899.99"), 1L);

            when(productRepository.findById(1L)).thenReturn(Optional.of(iphone));
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
            when(productMapper.toResponse(iphone)).thenReturn(iphoneResponse);

            productService.update(1L, request);

            // Dirty checking — update() không gọi save() tường minh
            verify(productRepository, never()).save(any());

            // Verify state của entity đã được mutate đúng
            assertThat(iphone.getName()).isEqualTo("iPhone 15 Pro Updated");
            assertThat(iphone.getPrice()).isEqualByComparingTo("899.99");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist or is inactive")
        void shouldThrowWhenProductNotFoundOrInactive() {
            UpdateProductRequest request = new UpdateProductRequest(
                    "Updated", "desc", new BigDecimal("99.99"), 1L);

            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when new category does not exist")
        void shouldThrowWhenNewCategoryNotFound() {
            UpdateProductRequest request = new UpdateProductRequest(
                    "Updated", "desc", new BigDecimal("99.99"), 99L);

            when(productRepository.findById(1L)).thenReturn(Optional.of(iphone));
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(1L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // -----------------------------------------------------------------------
    // delete()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should soft-delete product by setting isActive to false")
        void shouldSoftDeleteProduct() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(iphone));

            productService.delete(1L);

            // Soft-delete — isActive phải là false sau khi delete
            assertThat(iphone.isActive()).isFalse();

            // Hard-delete không được gọi
            verify(productRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product does not exist")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.delete(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}