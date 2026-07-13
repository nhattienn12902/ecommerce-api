# CLAUDE.md — ecommerce-api Project Instructions

> This file defines all technical decisions, conventions, and structure for the `ecommerce-api` project.
> When there is a conflict between this file and a question in chat — this file takes priority.

---

## 1. Project Overview

- **Project name:** ecommerce-api
- **Description:** RESTful API for an E-Commerce platform — managing products, shopping cart, orders, and users.
- **Purpose:** Personal project / portfolio — showcase Java Backend + DevOps skills for Fresher/Junior Backend Developer positions.
- **Current phase:** Well-structured Monolith. No microservices.
- **GitHub:** Public repo — code must always be clean and production-ready.

---

## 2. Tech Stack

### Backend
| Component | Technology | Notes |
|---|---|---|
| Language | Java 21 | Use modern features: Records, Pattern Matching, Text Blocks |
| Framework | Spring Boot 4.1.x | Locked via `spring-boot-starter-parent` in `pom.xml`. Do not downgrade to 3.x |
| Build tool | Maven | Do not use Gradle |
| Database | PostgreSQL 16 | Production: AWS RDS |
| Migration | Flyway | Mandatory — do not create tables manually |
| ORM | Spring Data JPA / Hibernate | |
| Cache | Redis | Production: AWS ElastiCache. Local: Docker |
| Security | Spring Security (version bundled with Spring Boot 4.1.x) + JWT | Stateless, Refresh Token rotation. Confirm exact Spring Security major version when Phase 2 starts |
| Mapping | MapStruct | Do not use ModelMapper |
| Boilerplate | Lombok | |
| Validation | Jakarta Validation (Bean Validation) | |
| API Docs | SpringDoc OpenAPI (Swagger UI) | |
| File Storage | AWS S3 | |
| Email | Spring Mail (SMTP) | Async via @Async |

### DevOps & Infrastructure
| Component | Technology |
|---|---|
| Containerization | Docker + Docker Compose (local dev) |
| CI/CD | GitHub Actions |
| Cloud | AWS (EC2, RDS, ElastiCache, S3, SSM, CloudWatch) |
| Reverse Proxy | Nginx + Let's Encrypt (HTTPS) |
| Monitoring | Prometheus + Grafana + Spring Boot Actuator |
| Secret Management | AWS SSM Parameter Store — do not use .env files on server |

### Testing
| Component | Technology |
|---|---|
| Unit Test | JUnit 5 + Mockito + AssertJ |
| Integration Test | Spring Boot Test + Testcontainers |

---

## 3. Project Structure

Package-by-Feature aligned with Clean Architecture. Each feature is a self-contained package.

```
ecommerce-api/
├── src/
│   ├── main/
│   │   ├── java/com/nhattienn/ecommerce/
│   │   │   ├── EcommerceApiApplication.java
│   │   │   │
│   │   │   ├── common/                        # Shared across features
│   │   │   │   ├── exception/                 # GlobalExceptionHandler, custom exceptions
│   │   │   │   ├── response/                  # ApiResponse, PageResponse, ErrorResponse
│   │   │   │   ├── security/                  # JwtService, JwtAuthFilter, SecurityConfig
│   │   │   │   └── util/                      # Utility classes
│   │   │   │
│   │   │   ├── user/                          # Feature: User management
│   │   │   │   ├── User.java                  # Entity
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── UserController.java
│   │   │   │   └── dto/
│   │   │   │       ├── UserResponse.java
│   │   │   │       └── UpdateProfileRequest.java
│   │   │   │
│   │   │   ├── auth/                          # Feature: Authentication
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── RefreshToken.java          # Entity
│   │   │   │   ├── RefreshTokenRepository.java
│   │   │   │   └── dto/
│   │   │   │       ├── LoginRequest.java
│   │   │   │       ├── RegisterRequest.java
│   │   │   │       └── AuthResponse.java
│   │   │   │
│   │   │   ├── product/                       # Feature: Product catalog
│   │   │   │   ├── Product.java
│   │   │   │   ├── ProductRepository.java
│   │   │   │   ├── ProductService.java
│   │   │   │   ├── ProductController.java
│   │   │   │   └── dto/
│   │   │   │
│   │   │   ├── category/                      # Feature: Category
│   │   │   ├── cart/                          # Feature: Shopping cart (Redis)
│   │   │   ├── order/                         # Feature: Order management
│   │   │   ├── storage/                       # Feature: S3 file upload
│   │   │   └── notification/                  # Feature: Async email
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql
│   │           └── V2__seed_data.sql
│   │
│   └── test/
│       └── java/com/nhattienn/ecommerce/
│           ├── auth/
│           │   └── AuthServiceTest.java
│           └── product/
│               └── ProductServiceTest.java
│
├── docker-compose.yml                         # Local dev: PostgreSQL + Redis
├── docker-compose.prod.yml                    # Production reference
├── Dockerfile                                 # Multi-stage build
├── .github/
│   └── workflows/
│       ├── ci.yml                             # Test + Build + Push image
│       └── cd.yml                             # Deploy to EC2
├── nginx/
│   └── nginx.conf
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
├── postman/
│   └── ecommerce-api.postman_collection.json
└── CLAUDE.md
```

**Structure rules:**
- Each feature contains its own Entity, Repository, Service, Controller, and `dto/` directory.
- Do not place Entities in `common/` — Entities belong to their feature.
- Shared code (exception, response format, security, util) → `common/`.
- Do not organize packages by layer (`controllers/`, `services/`, `repositories/`) — this is an anti-pattern.

---

## 4. Coding Conventions

### Naming
- **Class:** PascalCase — `ProductService`, `OrderController`, `CartItem`
- **Method/Variable:** camelCase — `findById`, `totalPrice`, `isAvailable`
- **Constant:** UPPER_SNAKE_CASE — `MAX_CART_ITEMS`, `JWT_EXPIRATION`
- **Package:** lowercase — `com.nhattienn.ecommerce.product`
- **Flyway migration:** `V{version}__{description}.sql` — `V1__init_schema.sql`
- **DTO suffix:** Request (input) / Response (output) — `CreateProductRequest`, `ProductResponse`

### Code principles
- **Clean Code:** names must be self-descriptive — no comments explaining *what* the code does
- **SOLID:** especially Single Responsibility — Service does not contain mapping logic, Controller does not contain business logic
- **DRY:** extract method/class when logic is repeated 2 or more times
- **KISS:** do not over-engineer. Do not add abstraction layers before they are needed

### Layer responsibilities
```
Controller   → receive request, validate input (@Valid), call Service, return response
Service      → business logic, transaction management, call Repository
Repository   → database queries only, no logic
Entity       → database mapping only, no business logic
DTO          → data transfer only, no logic
Mapper       → convert Entity ↔ DTO (using MapStruct)
```

Controller must not contain business logic.
Service must not return Entities — always convert to DTO before returning to Controller.

---

## 5. API Design

### Base URL
```
/api/v1/{resource}
```

### Endpoint naming
```
GET    /api/v1/products           # List with pagination
GET    /api/v1/products/{id}      # Get by ID
POST   /api/v1/products           # Create
PUT    /api/v1/products/{id}      # Full update
PATCH  /api/v1/products/{id}      # Partial update
DELETE /api/v1/products/{id}      # Delete

POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
```

### Unified response format
All API responses share the same structure:

```json
// Success — single object
{
  "success": true,
  "data": { ... },
  "message": "Product created successfully."
}

// Success — paginated list
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "last": false
  }
}

// Error
{
  "success": false,
  "timestamp": "2026-07-12T10:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Validation failed.",
  "path": "/api/v1/products",
  "errors": [
    { "field": "name", "message": "Product name must not be blank." }
  ],
  "traceId": "abc123..."
}
```

### HTTP Status codes
| Status | When to use |
|---|---|
| 200 OK | Successful GET, PUT, PATCH |
| 201 Created | Successful POST (resource created) |
| 204 No Content | Successful DELETE |
| 400 Bad Request | Validation error, malformed request |
| 401 Unauthorized | Not authenticated (missing or invalid token) |
| 403 Forbidden | Authenticated but not authorized |
| 404 Not Found | Resource does not exist |
| 409 Conflict | Duplicate resource (e.g. email already exists) |
| 422 Unprocessable Entity | Business rule violation |
| 500 Internal Server Error | Unexpected server error |

---

## 6. Database & Flyway

### Rules
- **Flyway is mandatory** — do not create or modify tables manually or via Hibernate `ddl-auto`
- Use `spring.jpa.hibernate.ddl-auto=validate` in production — never use `create` or `create-drop`
- Migration files are append-only — never modify a file that has already been committed

### Database naming conventions
- Table: `snake_case`, plural — `users`, `products`, `order_items`
- Column: `snake_case` — `created_at`, `total_price`, `product_id`
- Primary key: `id` (UUID or BIGSERIAL depending on entity)
- Foreign key: `{table_singular}_id` — `user_id`, `product_id`, `order_id`
- Index: `idx_{table}_{column}` — `idx_products_category_id`
- Unique constraint: `uq_{table}_{column}` — `uq_users_email`

### Required columns for every Entity
```java
@CreationTimestamp
private LocalDateTime createdAt;

@UpdateTimestamp
private LocalDateTime updatedAt;
```

---

## 7. Security Rules

Apply throughout — proactively flag violations even when not asked.

- **No hardcoded secrets** — do not hardcode secrets, passwords, API keys, or tokens in source code. Use environment variables or AWS SSM Parameter Store.
- **JWT:** Short-lived Access Token (15–30 minutes) + long-lived Refresh Token (7–30 days). Refresh Token stored in DB and must be revocable.
- **Password:** Always hash with BCrypt. Never store plaintext.
- **Authorization:** Every endpoint must explicitly declare its access rules — do not accidentally leave sensitive endpoints as `permitAll()`.
- **Input validation:** Validate at the Controller layer using `@Valid` + Bean Validation. Never trust client input.
- **SQL:** Never concatenate strings into queries — use JPQL parameter binding or Spring Data methods.
- **Error response:** Never expose stack traces, class names, or internal system details to clients in production.
- **S3:** Use presigned URLs for file access — never make S3 buckets publicly accessible.

---

## 8. Transaction & Concurrency

- Place `@Transactional` at the **Service layer** only — not in Controller or Repository.
- Read-only operations: `@Transactional(readOnly = true)` — required for all read-only methods.
- **Optimistic Locking:** Use `@Version` on the `Product` entity to prevent overselling during concurrent checkouts.
- Do not use Pessimistic Locking unless there is a clear, documented reason.
- Do not call external services (HTTP, email, S3) inside a `@Transactional` block — this can hold the transaction open too long.

---

## 9. Exception Handling

Centralized handling — do not scatter exception handling across multiple layers.

```
common/exception/
├── GlobalExceptionHandler.java      # @RestControllerAdvice
├── ResourceNotFoundException.java   # 404
├── DuplicateResourceException.java  # 409
├── BusinessException.java           # 422 — base class for business rule violations
├── InsufficientStockException.java  # extends BusinessException
└── UnauthorizedException.java       # 401
```

**Rules:**
- Service only `throws` exceptions — it does not format error responses.
- Controller does not `try-catch` business exceptions — let `GlobalExceptionHandler` handle them.
- Custom exceptions must have clear, descriptive messages suitable for logging and debugging.

---

## 10. Caching Strategy (Redis)

- **Cart:** store the full cart in Redis with key `cart:{userId}`. TTL: 7 days.
- **Product catalog:** cache popular product listings. TTL: 10 minutes.
- **Invalidation:** evict cache when a product is updated or deleted.
- Do not cache sensitive data (user info, orders, payment details).
- Use Spring Cache annotations: `@Cacheable`, `@CacheEvict` backed by Redis.

---

## 11. Testing

- **Unit Tests:** mandatory for the Service layer — all business logic must be tested.
- **Do not test:** Controllers (covered by integration tests), Repositories (covered by Testcontainers), simple getters/setters.
- **Naming:** describe behavior — `shouldThrowExceptionWhenProductIsOutOfStock()`, not `testCheckout()`.
- **Pattern:** Arrange–Act–Assert (AAA).
- **Target coverage:** Service layer >= 80%.
- Avoid over-mocking: if a method requires mocking too many dependencies to test, that is a sign the method is doing too much.

---

## 12. Git & Commit Convention

### Commit messages — Conventional Commits
```
feat(auth): implement JWT refresh token rotation
fix(order): prevent negative stock when concurrent checkout
refactor(product): extract price calculation to domain method
test(cart): add unit tests for cart service
docs(readme): add architecture diagram
chore(deps): upgrade spring boot to 3.3.x
ci: add GitHub Actions CD workflow
```

**Rules:**
- English only, imperative verb form (implement, fix, add, remove).
- Describe the *purpose* of the change, not the implementation details (not "changed line 42").
- One commit does one thing — do not bundle unrelated changes.
- Never commit: `.env` files, secrets, build output, IDE config (`.idea/`).

### Branch naming
```
feature/auth-jwt
feature/product-api
fix/cart-redis-ttl
chore/setup-github-actions
```

### Pull Request
Every PR must include: Summary, Changes, Testing, Notes (Breaking changes if any).

---

## 13. Docker & Deployment

### Local dev
```bash
# Start PostgreSQL + Redis
docker compose up -d

# Run the application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Dockerfile — multi-stage (mandatory)
```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Runtime — JRE only, not JDK
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### GitHub Actions — CI pipeline
Trigger: push or PR to `main` or `develop`
Steps: Checkout → Setup Java 21 → Maven test → Maven build → Docker build → Push to ECR

### GitHub Actions — CD pipeline
Trigger: CI passes on `main`
Steps: SSH into EC2 → pull new image → `docker compose up -d` → health check

### Secrets (never commit to repo)
Store in GitHub Secrets:
- `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
- `EC2_HOST`, `EC2_SSH_KEY`
- `JWT_SECRET`
- `DB_PASSWORD`

---

## 14. Environment Configuration

```yaml
# application.yml — shared config
spring:
  profiles:
    active: local  # override via env var when deploying

# application-local.yml — local development
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce
    username: postgres
    password: postgres  # local only — never commit production credentials
  data:
    redis:
      host: localhost
      port: 6379

# application-prod.yml — production
# Never hardcode values — read from env vars or AWS SSM
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

---

## 15. Domain Model (E-Commerce)

### Entities & Relationships
```
User ──────────── RefreshToken (1:N)
User ──────────── Order (1:N)
Order ─────────── OrderItem (1:N)
OrderItem ─────── Product (N:1)
Product ───────── Category (N:1)
Product ───────── ProductImage (1:N)
Cart (Redis) ──── CartItem (embedded, not persisted to DB)
```

### Order State Machine
```
PENDING → CONFIRMED → SHIPPED → DELIVERED
    └──────────────────────────→ CANCELLED
```
- **PENDING:** order placed, awaiting confirmation
- **CONFIRMED:** confirmed, being prepared
- **SHIPPED:** out for delivery
- **DELIVERED:** received by customer
- **CANCELLED:** cancelled (only allowed from PENDING or CONFIRMED)

---

## 16. Development Order (Roadmap)

```
Phase 1 (Week 1):   Project setup, DB schema, Docker Compose, Flyway, GlobalExceptionHandler
Phase 2 (Week 2-3): Auth/JWT, Product + Category API, S3 upload, Unit Tests
Phase 3 (Week 4-5): Cart (Redis), Checkout + Transaction, Optimistic Locking, Order state machine
Phase 4 (Week 6):   Dockerfile, GitHub Actions CI/CD
Phase 5 (Week 7):   AWS deploy (EC2, RDS, ElastiCache), Nginx, Prometheus + Grafana
Phase 6 (Week 8):   README, Postman collection, seed data, code review, interview prep
```

Do not skip phases. Do not start the next phase until the current phase deliverable is complete.

---

## 17. Out of Scope — Do Not Implement

- ❌ Microservices — a well-structured monolith is sufficient for this portfolio goal
- ❌ Kubernetes — over-engineering for the current scope
- ❌ Kafka / message queue — not needed; `@Async` is sufficient for email notifications
- ❌ Real payment gateway integration (Stripe, MoMo) — mock payment flow is enough
- ❌ Frontend — this is an API-only backend project
- ❌ `spring.jpa.hibernate.ddl-auto=create` or `update` — use Flyway instead
- ❌ Hardcoded secrets of any kind in source code
- ❌ Committing `.env` files containing credentials to the repository

---

*This file is maintained alongside the project development.
When a new technical decision is made (adding a dependency, changing a convention, etc.) — update this file before writing code.*