# product-service

RESTful API for managing products in the store platform.

## Stack

- Java 25 / Spring Boot 4.0.3
- PostgreSQL (schema: `products`)
- Flyway migrations
- Prometheus metrics at `/actuator/prometheus`

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/products` | Create a product |
| `GET` | `/products` | List all products |
| `GET` | `/products/{id}` | Get product by ID |
| `DELETE` | `/products/{id}` | Delete a product |

## Running locally

```bash
cp .env.example .env
mvn spring-boot:run
```

## Docker

```bash
mvn -B -DskipTests clean package
docker build -t cheqr/product .
docker run -p 8080:8080 --env-file .env cheqr/product
```
