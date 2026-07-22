# CLAUDE.md — ecommerce-api Project Instructions

> This file defines all technical decisions, conventions, and structure for the ecommerce-api project.
> When there is a conflict between this file and chat instructions, this file takes priority.

---

# 1. Project Overview

## Project Information

* Project name: ecommerce-api
* Type: RESTful E-Commerce Backend API
* Purpose: Portfolio project for Java Backend + DevOps skills
* Architecture: Well-structured Monolith
* Do not implement microservices

Goals:

* Demonstrate production-ready Spring Boot development.
* Apply Clean Architecture principles.
* Showcase DevOps skills with Docker, AWS, CI/CD, Monitoring.

---

# 2. Tech Stack

## Backend

| Component         | Technology                  | Rule                                       |
| ----------------- | --------------------------- | ------------------------------------------ |
| Language          | Java 21                     | Use Records, Pattern Matching, Text Blocks |
| Framework         | Spring Boot 4.1.x           | Do not downgrade                           |
| Build Tool        | Maven                       | No Gradle                                  |
| Database          | PostgreSQL 16               | Production: AWS RDS                        |
| Migration         | Flyway                      | Mandatory                                  |
| ORM               | Spring Data JPA + Hibernate |                                            |
| Cache             | Redis                       | AWS ElastiCache production                 |
| Security          | Spring Security + JWT       | Access Token + Refresh Token rotation      |
| Mapping           | MapStruct                   | Default mapping solution                   |
| Boilerplate       | Lombok                      |                                            |
| Validation        | Jakarta Validation          |                                            |
| API Documentation | SpringDoc OpenAPI           |                                            |
| Storage           | AWS S3                      | Use presigned URLs                         |
| Email             | Spring Mail                 | Async with @Async                          |

---

# 3. Project Structure

Use package-by-feature.

Do NOT organize by technical layers:

❌ controllers/
❌ services/
❌ repositories/

Use:

```
com.nhattienn.ecommerce

├── common
│   ├── exception
│   ├── response
│   ├── security
│   └── util
│
├── auth
│   ├── AuthController
│   ├── AuthService
│   ├── RefreshToken
│   └── dto
│
├── user
│
├── product
│   ├── Product.java
│   ├── ProductRepository.java
│   ├── ProductService.java
│   ├── ProductController.java
│   ├── ProductMapper.java
│   └── dto
│
├── category
│
├── cart
│
├── order
│
├── storage
│
└── notification
```

Rules:

* Entity belongs to its feature.
* Shared code only goes into common.
* Each feature owns its DTO, mapper, repository, service, controller.

---

# 4. Coding Conventions

## Naming

Class:

```
ProductService
OrderController
CartItem
```

Method:

```
findById()
createProduct()
```

Constant:

```
JWT_EXPIRATION
MAX_CART_ITEMS
```

Database:

```
snake_case
```

Migration:

```
V1__init_schema.sql
V2__seed_data.sql
```

---

# 5. Architecture Rules

## Layer Responsibility

```
Controller
    |
    | request validation
    | call service
    |
Service
    |
    | business logic
    | transaction management
    |
Repository
    |
    | database access
```

Rules:

### Controller

Responsible for:

* Receive request.
* Validate input.
* Call service.
* Return response.

No business logic.

---

### Service

Responsible for:

* Business rules.
* Transaction handling.
* Repository interaction.

Service must NOT return Entity.

Always return DTO.

---

### Repository

Responsible for:

* Database queries only.

No business logic.

---

### Entity

Responsible for:

* Database mapping.

Avoid business logic inside Entity.

---

### DTO

Responsible for:

* Data transfer only.

No business logic.

---

# 6. Entity ↔ DTO Mapping Rules

## Default Rule

Use MapStruct for Entity ↔ DTO conversion.

Example:

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product product);

}
```

---

## Static Factory Exception

Static factory methods are allowed only for simple immutable DTOs.

Example:

```java
public record CategoryResponse(
        Long id,
        String name
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName()
        );
    }
}
```

Allowed when:

* DTO contains only flat fields.
* No nested object mapping.
* No collection transformation.
* No custom conversion logic.

---

Use MapStruct when mapping includes:

* Nested objects.
* Lists.
* Field rename.
* Multiple source objects.
* Custom mapping rules.

Example:

```
Product
 |
 |-- Category.name
 |
 |-- ProductImage list
```

must use MapStruct.

---

# 7. API Design

Base URL:

```
/api/v1/{resource}
```

Example:

```
GET    /api/v1/products
GET    /api/v1/products/{id}
POST   /api/v1/products
PUT    /api/v1/products/{id}
PATCH  /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

---

# 8. Product API

Endpoints:

```
GET    /api/v1/products
GET    /api/v1/products/{id}

POST   /api/v1/products       ADMIN
PUT    /api/v1/products/{id}  ADMIN
DELETE /api/v1/products/{id} ADMIN
```

Delete behavior:

Product uses soft delete:

```
is_active = false
```

---

Product filters:

```
page
size
sort

categoryId

minPrice
maxPrice

keyword
```

Keyword searches:

```
product.name
```

---

# 9. Category API

Endpoints:

```
GET    /api/v1/categories
GET    /api/v1/categories/{id}

POST   /api/v1/categories       ADMIN
PUT    /api/v1/categories/{id}  ADMIN
DELETE /api/v1/categories/{id} ADMIN
```

Delete rule:

A category cannot be deleted if products reference it.

Return:

```
409 Conflict
```

---

# 10. Database Rules

Mandatory:

* Flyway only.
* Never modify committed migration files.
* Hibernate ddl-auto = validate.

Naming:

Tables:

```
products
categories
order_items
```

Columns:

```
created_at
updated_at
product_id
```

---

Every entity requires:

```
created_at
updated_at
```

Database trigger can manage timestamps.

---

# 11. Security Rules

Never:

* Hardcode secrets.
* Store plaintext password.
* Expose internal errors.

JWT:

Access Token:

```
15-30 minutes
```

Refresh Token:

```
7-30 days
stored in database
revocable
rotation enabled
```

Password:

```
BCrypt
```

---

# 12. Transaction Rules

@Transactional only in Service layer.

Read:

```java
@Transactional(readOnly = true)
```

Write:

```java
@Transactional
```

Never call:

* Email
* HTTP API
* S3

inside transaction.

---

Optimistic locking:

Product uses:

```java
@Version
private Long version;
```

for inventory concurrency.

---

# 13. Exception Handling

Location:

```
common/exception
```

Classes:

```
GlobalExceptionHandler
ResourceNotFoundException
DuplicateResourceException
BusinessException
UnauthorizedException
InsufficientStockException
```

Rules:

Service throws exceptions.

Controller never catches business exceptions.

GlobalExceptionHandler formats responses.

---

# 14. Testing

Required:

Unit:

```
JUnit 5
Mockito
AssertJ
```

Integration:

```
Spring Boot Test
Testcontainers
```

Rules:

Test:

* Service business logic.

Do not test:

* Getters/setters.
* Simple DTO.
* Repository directly.

Naming:

```
shouldThrowExceptionWhenProductNotFound()
```

Pattern:

```
Arrange
Act
Assert
```

Target:

```
Service coverage >= 80%
```

---

# 15. Git Convention

Use Conventional Commits.

Examples:

```
feat(product): implement product api

fix(auth): handle refresh token reuse

test(product): add product service tests

refactor(category): simplify category mapping
```

Rules:

* English only.
* One commit = one purpose.
* Never commit secrets.
* Never commit .env.

---

# 16. Development Roadmap

## Phase 1

* Project setup
* PostgreSQL
* Flyway
* Docker Compose
* Exception handling

## Phase 2

* JWT Authentication
* Product API
* Category API
* S3 integration
* Unit tests

## Phase 3

* Redis Cart
* Checkout
* Orders
* Optimistic locking

## Phase 4

* Docker image
* GitHub Actions

## Phase 5

* AWS deployment
* Monitoring

## Phase 6

* Documentation
* Interview preparation

---

# 17. Out Of Scope

Do not implement:

* Microservices
* Kubernetes
* Kafka
* Real payment gateway
* Frontend
* Public S3 bucket
* Hibernate auto migration

---

This file must be updated whenever a new architectural decision is made.
