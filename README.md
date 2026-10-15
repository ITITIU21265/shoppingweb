# AI-Integrated E-Clothing E-Commerce Web Application

A multi-vendor fashion marketplace implemented as a strict layered modular monolith with Spring Boot. The application exposes two client surfaces over one shared business and persistence model:

- A server-rendered Web MVC application using Thymeleaf and stateful Spring Security sessions.
- A REST API using stateless JWT bearer authentication and JSON DTO contracts.

The main engineering features are an item-to-item hybrid recommendation engine, transactional multi-vendor checkout, database-level pessimistic inventory locking, seller self-purchase prevention, and role-based access control for `ADMIN`, `SELLER`, and `CUSTOMER` accounts.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 25 |
| Application framework | Spring Boot 4.0.2 |
| Web | Spring MVC, REST controllers, Thymeleaf |
| Security | Spring Security, stateful sessions, JWT |
| Persistence | Spring Data JPA, Hibernate, MySQL |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail with SMTP |
| Frontend | Thymeleaf templates, CSS, JavaScript, optional Tailwind tooling |
| Testing | JUnit 5, Spring Boot Test, MockMvc, Mockito |

## Architectural Overview

The application follows a layered modular-monolith architecture. Business domains are separated by package while remaining deployed as one Spring Boot application and using one shared MySQL database.

```text
Web Browser / REST Client
          |
          v
Spring Security Filter Chains
          |
          v
Web MVC Controllers / REST API Controllers
          |
          v
Application and Domain Services (@Transactional boundaries)
          |
          v
Spring Data JPA Repositories
          |
          v
Shared MySQL Database
```

Controllers depend on services rather than repositories. Services own validation, authorization-sensitive business rules, orchestration, and transaction boundaries. Repositories encapsulate persistence access.

### Main Packages

```text
src/main/java/com/web/shoppingweb/
|-- config/                   # Optional startup/demo data initialization
|-- controller/
|   |-- api/                 # JSON REST endpoints
|   `-- web/                 # Thymeleaf MVC endpoints
|-- dto/                     # API and form contracts
|-- entity/                  # JPA entities grouped by domain
|-- exception/               # API and Web MVC exception handling
|-- repository/              # Spring Data JPA repositories
|-- security/                # Filter chains, JWT, RBAC helpers
|-- service/                 # Service contracts and recommendation engine
`-- service/impl/            # Transactional business implementations
```

## Core Engineering Features

### Hybrid Item-to-Item Recommendation Engine

`ProductRecommendationService` combines two recommendation pipelines:

1. **Content-based filtering**
   - Reads product names and descriptions.
   - Tokenizes normalized clothing metadata.
   - Builds TF-IDF vectors.
   - Uses cosine similarity to compare products.

2. **Item-based collaborative filtering**
   - Reads historical `OrderItem` records.
   - Treats each order as a basket of distinct products.
   - Builds product co-occurrence counts.
   - Normalizes co-occurrence using cosine similarity.

The final score is:

```text
HybridScore = 0.3 * ContentSimilarity + 0.7 * CollaborativeSimilarity
```

The content component provides recommendations for new catalog items that do not yet have purchase history, reducing the new-item cold-start problem. The collaborative component gives greater weight to observed customer purchasing behavior.

The model is rebuilt:

- Once after application startup.
- Every day at `02:00` through Spring scheduling.

The resulting read-only TF-IDF vectors and collaborative matrix are held in memory. Product detail pages request the top four related products.

### Dual-Protocol Spring Security

`SecurityConfig` defines two ordered `SecurityFilterChain` beans:

| Chain | Scope | Authentication | Session policy |
| --- | --- | --- | --- |
| API chain | `/api/**` | JWT bearer token | `STATELESS` |
| Web chain | All remaining routes | Form login and `JSESSIONID` | Stateful default session behavior |

The API chain disables CSRF, validates JWTs through `JwtAuthenticationFilter`, and returns JSON authentication/access-denied responses. The Web MVC chain uses form login, role-aware redirects, logout session invalidation, CSRF protection, and active-user status enforcement.

#### Shopping Access Rules

- `CUSTOMER` and `SELLER` may use cart, saved-item, checkout, and personal-order features.
- `ADMIN` is explicitly denied access to customer shopping features.
- Sellers may act as customers but cannot add or checkout their own inventory.
- Self-purchase validation is enforced in both `CartServiceImpl` and `OrderServiceImpl`.

### Transactional Multi-Vendor Checkout

`OrderServiceImpl.checkout(...)` executes inside a Spring `@Transactional` boundary.

The checkout workflow:

1. Loads the authenticated user's active cart and selected cart items.
2. Sorts selected variant IDs to establish a consistent lock order.
3. Fetches variants through `ProductVariantRepository.findAllByIdInForUpdate(...)`.
4. Applies `@Lock(LockModeType.PESSIMISTIC_WRITE)` so concurrent transactions cannot decrement the same inventory simultaneously.
5. Revalidates product availability, variant status, quantity, stock, and seller self-purchase rules.
6. Deducts stock while the variants remain locked.
7. Creates one master `Order`.
8. Groups checkout lines by `Product.seller.id`.
9. Creates one `OrderSeller` vendor sub-order per seller.
10. Creates snapshot-style `OrderItem` records linked to both the master order and seller sub-order.
11. Marks the checked-out cart as `ORDERED` and moves unselected items into a fresh active cart.
12. Commits all changes atomically or rolls everything back on failure.

The repository lock query eagerly fetches the related product and seller so inventory validation and seller grouping occur within the same transaction.

## Business Modules

| Module | Responsibilities |
| --- | --- |
| Authentication and users | Registration, login, JWT refresh, logout, profile management, password reset OTP, roles, account status |
| Catalog | Product listing, product detail, seller-owned product management, categories, variants |
| Supplier onboarding | Seller application, administrator approval/rejection, seller role assignment |
| Saved items | User-specific saved product collection |
| Cart | Active cart lifecycle, default variant selection, quantity management, self-purchase prevention |
| Orders | Atomic checkout, stock validation, master orders, vendor sub-orders, item snapshots |
| Recommendation | TF-IDF and order co-occurrence hybrid related-product ranking |
| Dashboards | Administrator and seller operational views |

## Core Persistence Model

The JPA model maps to the following primary tables:

| Domain | Tables |
| --- | --- |
| Users and security | `users`, `roles`, `user_roles`, `refresh_tokens`, `password_resets` |
| Catalog | `categories`, `products`, `product_variants` |
| Supplier | `suppliers` |
| Shopping | `saved_items`, `carts`, `cart_items` |
| Multi-vendor ordering | `orders`, `order_sellers`, `order_items` |

The multi-vendor order hierarchy is:

```text
User
  `-- Cart
       `-- CartItem -> ProductVariant -> Product -> Seller

User
  `-- Order (master customer order)
       |-- OrderSeller (one vendor-specific sub-order per seller)
       `-- OrderItem -> OrderSeller + Product + ProductVariant
```

`OrderItem` stores product title, name, slug, image URL, SKU, quantity, unit price, and line total snapshot fields so order history remains meaningful when catalog data changes.

## Roles and Access

| Capability | Public | CUSTOMER | SELLER | ADMIN |
| --- | ---: | ---: | ---: | ---: |
| Browse catalog and product details | Yes | Yes | Yes | Yes |
| Manage profile | No | Yes | Yes | Yes |
| Saved items, cart, checkout, personal orders | No | Yes | Yes | No |
| Purchase own inventory | No | N/A | No | N/A |
| Create/update/delete products | No | No | Yes, ownership checked | Yes |
| Seller dashboard | No | No | Yes | No |
| Administrator endpoints and dashboard | No | No | No | Yes |
| Apply to become a seller | No | Yes | No | No |

## REST API Summary

### Public Endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Authenticate and receive access/refresh tokens |
| `POST` | `/api/auth/register` | Register a customer account |
| `POST` | `/api/auth/forgot-password` | Request password-reset OTP |
| `POST` | `/api/auth/reset-password` | Reset password using OTP |
| `POST` | `/api/auth/refresh` | Rotate refresh token and receive a new JWT |
| `GET` | `/api/products` | List active products |
| `GET` | `/api/products/{slug}` | Get an active product |

### Authenticated Endpoints

| Method | Endpoint | Access |
| --- | --- | --- |
| `GET` | `/api/auth/me` | Any authenticated user |
| `POST` | `/api/auth/logout` | Any authenticated user |
| `GET`, `PUT` | `/api/users/profile` | Any authenticated user |
| `PUT` | `/api/users/change-password` | Any authenticated user |
| `DELETE` | `/api/users/account` | Any authenticated user |
| `GET`, `POST`, `DELETE` | `/api/saved` | CUSTOMER or SELLER, excluding ADMIN |
| `GET` | `/api/cart` | CUSTOMER or SELLER, excluding ADMIN |
| `POST` | `/api/cart/items` | CUSTOMER or SELLER, excluding ADMIN |
| `PUT`, `DELETE` | `/api/cart/items/{variantId}` | CUSTOMER or SELLER, excluding ADMIN |
| `GET`, `POST` | `/api/orders` | CUSTOMER or SELLER, excluding ADMIN |
| `GET` | `/api/orders/{orderId}` | Order owner |
| `POST` | `/api/products` | ADMIN or SELLER |
| `PUT`, `DELETE` | `/api/products/{id}` | ADMIN or owning SELLER |
| `GET` | `/api/admin/users` | ADMIN |
| `PUT` | `/api/admin/users/{id}/role` | ADMIN |
| `PATCH` | `/api/admin/users/{id}/status` | ADMIN |

### API Examples

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "secure-password"
}
```

The current login response exposes the JWT in the `token` field and also returns a refresh token.

Add a product to the active cart:

```http
POST /api/cart/items
Authorization: Bearer <token>
Content-Type: application/json

{
  "productId": 1001,
  "quantity": 1
}
```

Checkout selected variants:

```http
POST /api/orders
Authorization: Bearer <token>
Content-Type: application/json

{
  "selectedVariantIds": [501, 502],
  "shippingAddress": "123 Example Street"
}
```

Successful checkout returns `201 Created`.

## Web MVC Routes

| Route | Purpose |
| --- | --- |
| `/`, `/catalog`, `/products/{slug}` | Public catalog and product detail |
| `/auth/**` | Login, registration, forgot-password, reset-password |
| `/profile`, `/account/**` | Authenticated account management |
| `/saved/**`, `/cart/**`, `/checkout/**`, `/orders/**` | CUSTOMER or SELLER shopping flows, excluding ADMIN |
| `/supplier/register` | CUSTOMER supplier application |
| `/dashboard/**` | ADMIN or SELLER dashboard |
| `/admin/**` | Administrator operations |

## Prerequisites

- Java Development Kit 25
- MySQL 8.x
- Git
- Node.js and npm only when rebuilding optional Tailwind assets

## Configuration

The application reads configuration from environment variables with development defaults where defined.

| Variable | Purpose | Development fallback |
| --- | --- | --- |
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/clothing_shop?useSSL=false&serverTimezone=UTC` |
| `DB_USERNAME` | Database username | `root` |
| `DB_PASSWORD` | Database password | `root` |
| `JWT_SECRET` | JWT signing secret | Development-only fallback |
| `JWT_EXPIRATION` | JWT lifetime in milliseconds | `86400000` |
| `MAIL_USERNAME` | SMTP account used for password-reset OTP | Empty |
| `MAIL_PASSWORD` | SMTP application password | Empty |

Important configuration behavior:

- The server listens on port `8080`.
- `spring.jpa.hibernate.ddl-auto=none`; the MySQL schema must already exist.
- Demo seeding is disabled by default.
- SQL and Spring Security debug logging are currently enabled.
- Secrets must be supplied through environment variables or ignored local configuration and must never be committed.

## Running the Application

Create the database before starting the application:

```sql
CREATE DATABASE clothing_shop;
```

Because Hibernate schema generation is disabled, the required tables must also exist before startup.

PowerShell:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-password"
$env:JWT_SECRET = "replace-with-a-strong-256-bit-or-longer-secret"
.\mvnw.cmd spring-boot:run
```

Git Bash or Linux/macOS:

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-password
export JWT_SECRET=replace-with-a-strong-256-bit-or-longer-secret
./mvnw spring-boot:run
```

Open:

```text
http://localhost:8080/catalog
```

### Optional Demo Data

The optional local `SystemDataInitializer` can seed roles, demo users, categories, products, and variants only when the database tables exist and the relevant tables are empty.

```powershell
.\mvnw.cmd "-Dspring-boot.run.arguments=--app.seed.system-data=true" spring-boot:run
```

Demo accounts created by that initializer:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `Admin@123` | ADMIN |
| `seller` | `Seller@123` | SELLER |

Do not enable demo credentials in production.

## Testing

Run the full test suite:

```powershell
.\mvnw.cmd test
```

Run the single-checkout API integration test:

```powershell
.\mvnw.cmd -Dtest=CheckoutApiIntegrationTest test
```

Run the 50-buyer concurrency test:

```powershell
.\mvnw.cmd -Dtest=CheckoutConcurrencyIntegrationTest test
```

The concurrency integration test verifies that five available units produce exactly five successful orders while the remaining 45 requests fail with business-level out-of-stock responses. It also asserts that no `5xx` response occurs and inventory stops at zero.

Warning: the checkout integration tests use the configured MySQL database and mutate data. The current concurrency test expects `productId=189691075` and `variantId=596`, resets that variant stock to five, creates/reuses 50 test buyers, and creates successful orders. Run it only against a disposable or dedicated test database.

Other focused tests cover:

- Administrator denial from web and API shopping routes.
- Cart self-purchase prevention.
- Checkout self-purchase validation.
- Web exception handling.
- Application context startup.

## Current Operational Notes

- Recommendation training currently loads all products and order items into application memory. This is appropriate for the present monolith scope but should be converted to incremental or offline training for a much larger catalog.
- Recommendation training currently uses `productRepository.findAll()`, and request-time ranking does not filter inactive candidates. Until the service is changed to train and rank only active products, an inactive or soft-deleted product can remain eligible for recommendation.
- Recommendation data refreshes at startup and daily, not immediately after every order or catalog update.
- The hybrid recommendation engine is deterministic application-side machine learning; it does not call an external LLM or hosted AI API.
- REST administrator user-management endpoints currently delegate directly to `UserService` and can target administrator accounts or assign any existing role. The Web admin console applies narrower safeguards and only manages `CUSTOMER` and `SELLER` accounts.
- `ddl-auto=none` protects the existing schema but requires explicit schema management. A production deployment should use Flyway or Liquibase migrations.
- Use a dedicated test profile and test database before running integration or concurrency tests in CI.
- Disable verbose SQL and security debug logging in production.
- Rotate any credential that has ever been exposed and keep SMTP, database, and JWT secrets outside source control.
