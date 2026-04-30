# Product Service — Handoff

Hi Carlos! This repo has the full project scaffold ready. You only need to write
the business logic. Below is a clear picture of what's done and what you need to build.

---

## What's already done

| Item | Location |
|------|----------|
| Spring Boot 4.0.3 / Java 25 project | `pom.xml` |
| PostgreSQL + Flyway config (schema: `products`) | `src/main/resources/application.yaml` |
| Prometheus metrics at `/actuator/prometheus` | `pom.xml` + `application.yaml` |
| Dockerfile (`eclipse-temurin:25`) | `Dockerfile` |
| Jenkins CI/CD pipeline (`cheqr/product`) | `Jenkinsfile` |
| Kubernetes manifests | `k8s/` (secrets, configmap, deployment, service) |
| Gateway route `/products/**` → this service | already pushed to gateway-service |
| Docker Compose integration in root repo | `api/compose.yaml` in microservices root |

---

## What you need to implement

### 1. Flyway migration

Create `src/main/resources/db/migration/V1__create_products_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS products.product (
    id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    price   NUMERIC(10, 2) NOT NULL,
    unit    VARCHAR(50)  NOT NULL
);
```

### 2. Product entity

```
src/main/java/store/product/
├── Product.java          ← @Entity, fields: id (UUID), name, price, unit
├── ProductRepository.java ← extends JpaRepository<Product, UUID>
├── ProductService.java   ← business logic
├── ProductController.java ← REST endpoints
├── ProductRequest.java   ← record/DTO for POST body
└── ProductResponse.java  ← record/DTO for responses
```

### 3. REST endpoints to implement

All routes are already forwarded by the gateway. The gateway injects `id-account` as a
header on every authenticated request.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/products` | Required | Create a new product |
| `GET` | `/products` | Required | List all products |
| `GET` | `/products/{id}` | Required | Get product by ID |
| `DELETE` | `/products/{id}` | Required | Delete product by ID |

**POST /products — request body:**
```json
{ "name": "Tomato", "price": 10.12, "unit": "kg" }
```

**POST /products / GET /products/{id} — response:**
```json
{ "id": "0195abfb-...", "name": "Tomato", "price": 10.12, "unit": "kg" }
```

**DELETE /products/{id}** — returns `204 No Content`

### 4. Reading the authenticated user

The gateway injects a `id-account` header on every authenticated request. Read it in
your controller:

```java
@GetMapping("/products")
public List<ProductResponse> list(@RequestHeader("id-account") String idAccount) {
    // idAccount is the UUID of the logged-in user
}
```

### 5. Additional features (recommended)

- **Search by name:** `GET /products?name=tom` using `findByNameContainingIgnoreCase`
- **Input validation:** add `spring-boot-starter-validation` and `@Valid` + `@NotBlank`, `@Positive`
- **Role-based access:** header `id-account` is enough to identify the user; for admin vs user
  roles, add a role field to account and pass it via a second header from the gateway, or
  implement a simpler hardcoded admin list

---

## Running locally

```bash
# 1. Start the database (from microservices root)
docker compose -f api/compose.yaml up db -d

# 2. Copy and fill in env vars
cp .env.example .env
# Edit .env: DATABASE_HOST=localhost

# 3. Run the service
mvn spring-boot:run
```

Service starts at `http://localhost:8080`.

## Building and pushing Docker image

```bash
mvn -B -DskipTests clean package
docker build -t cheqr/product .
```

The Jenkinsfile will do this automatically on each push.

## K8s

Manifests are in `k8s/`. The secrets use base64 values — update them before deploying:
```bash
echo -n "yourpassword" | base64
```

---

## Questions?

Check the root repo docs: https://github.com/Microservice-Alex-Carlos-Lucas/microservices
or ping Alex.
