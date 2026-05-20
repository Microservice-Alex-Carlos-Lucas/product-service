# Arquitetura

## Visão da Plataforma

```
                          ┌─────────────────────────────────────┐
                          │           Trusted Layer             │
                          │                                     │
  internet  ──request──▶  │  gateway ──▶  auth                  │
                          │     │                               │
                          │     ├──▶  account ──▶  DB           │
                          │     │                               │
                          │     ├──▶  order ──▶  DB             │
                          │     │           │                   │
                          │     │           └─▶ product (Feign) │
                          │     │                               │
                          │     └──▶  product ──▶  PostgreSQL   │
                          │                  └──▶ Redis          │
                          └─────────────────────────────────────┘
```

O Product API fica dentro da Trusted Layer. O gateway é o único ponto de entrada público — valida o JWT, injeta `id-account` no header, e encaminha. Tanto o usuário (via gateway) quanto o `order-service` (via OpenFeign) consomem `/products/{id}` — ambos os caminhos passam pelo mesmo cache.

## Fluxo de uma requisição

```
Client
  │
  │  GET /products/{id}  (cookie: __store_jwt_token)
  ▼
Gateway (Spring Cloud Gateway)
  │
  ├─ AuthorizationFilter: extrai JWT do cookie
  ├─ POST /auth/solve  ──▶  Auth Service
  │       ◀── { idAccount: "abc-123" }
  ├─ Adiciona header  id-account: abc-123
  ▼
Product Service (Spring Boot, port 8080)
  │
  ├─ ProductController.get(id, idAccount)
  ├─ ProductService.get(id)  ──┐
  │                            │  @Cacheable("products", key="#id")
  │                            ▼
  │                       Spring Cache
  │                       Manager (Redis)
  │       cache hit ─────────┘
  │           │
  │           │ cache miss
  │           ▼
  ├─ ProductRepository.findById(id)
  │       ──▶  PostgreSQL (schema: products)
  │       ◀── Product { id, name, price, unit }
  │
  └─▶ { id, name, price, unit }
```

## Estrutura de arquivos

```
product-service/
├── src/main/java/store/product/
│   ├── ProductApplication.java   # Spring Boot entrypoint
│   ├── ProductController.java    # REST endpoints
│   ├── ProductService.java       # @Cacheable / @CachePut / @CacheEvict
│   ├── ProductRepository.java    # Spring Data JPA
│   ├── Product.java              # JPA entity (table: products.product)
│   ├── ProductRequest.java       # Bean Validation (record)
│   ├── ProductResponse.java      # DTO (record)
│   └── CacheConfig.java          # RedisCacheManager TTL 60s, JSON serializer
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/
│       └── V1__create_products_table.sql  # Flyway
├── k8s/
│   ├── configmap.yaml   # DATABASE_HOST/PORT/DB, REDIS_HOST/PORT
│   ├── secrets.yaml     # DATABASE_USERNAME/PASSWORD
│   ├── deployment.yaml  # 1 replica inicial, requests 250m/256Mi
│   ├── service.yaml     # ClusterIP :8080
│   └── hpa.yaml         # CPU 50%, 1–5 réplicas
├── Dockerfile           # eclipse-temurin:25
├── Jenkinsfile          # Build → Push (multi-arch) → Deploy to EKS
├── mkdocs.yml
└── pom.xml              # Spring Boot 4.0.3, Java 25
```

## Modelo de dados

Tabela `products.product` (schema isolado, criado por Flyway):

```sql
CREATE TABLE IF NOT EXISTS products.product (
    id      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name    VARCHAR(255) NOT NULL,
    price   NUMERIC(10, 2) NOT NULL,
    unit    VARCHAR(50)  NOT NULL
);
```

`id` é gerado no banco via `gen_random_uuid()` (extension `pgcrypto`/`uuid-ossp`). O campo `unit` é uma string livre (ex: `kg`, `un`, `L`) — o catálogo não restringe a unidade, isso fica como contrato externo.

## Decisões de design

### Por que Spring Boot 4 + Java 25?

- A disciplina permite Java e o framework já está padronizado no grupo (gateway, auth, account, order).
- Java 25 + Spring Boot 4 dão acesso a [Problem Details (RFC 7807)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) habilitados via `spring.mvc.problemdetails.enabled=true`, eliminando boilerplate de `@ControllerAdvice`.

### Por que o serviço não valida o JWT diretamente?

A validação de JWT é responsabilidade exclusiva do gateway (`AuthorizationFilter.java`). O product service confia no header `id-account` injetado pelo gateway — isso mantém o serviço simples e sem dependência do segredo JWT. O `id-account` é lido pelo controller mas atualmente não é usado para autorização (catálogo é compartilhado entre contas); fica disponível para auditoria e para futuras regras de ownership.

### Por que Redis e não cache local (Caffeine)?

O HPA escala o deployment de 1 a 5 réplicas. Cache local por pod significaria invalidação inconsistente: um `DELETE` num pod não despejaria a key no pod vizinho, e clientes leriam estado obsoleto por até 60s. O Redis **compartilhado** garante que `@CacheEvict` e `@CachePut` afetem todos os pods em tempo real.

### Por que `RedisCacheManager` customizado?

A configuração default do Spring Boot serializa valores via JDK (binário, frágil entre versões). `CacheConfig` força:

- **`GenericJackson2JsonRedisSerializer`** — payload JSON legível via `redis-cli` (debug + portabilidade)
- **TTL 60s** explícito — sem isso o default é "sem expiração"
- **Prefixo `products::`** — segrega caches caso outro serviço compartilhe a mesma instância Redis no futuro

### Tratamento de erros

| Situação | Comportamento |
|----------|---------------|
| Produto inexistente (`GET`/`DELETE`) | `404 Not Found` (Problem Details) |
| Body inválido (`POST`) | `400 Bad Request` com violações de `@Valid` |
| Header `id-account` ausente | `400 Bad Request` (`@RequestHeader` obrigatório) |
| Redis indisponível | Fail-fast: Spring Cache propaga a exceção. Aceitável — Redis cai junto com a plataforma, e o HPA reage por CPU |
| Postgres indisponível | `500 Internal Server Error` (não há fallback — produto é fonte da verdade) |

## Deploy na AWS

O serviço roda no **EKS** (`store-cluster`, `us-east-1`), atrás do gateway. Banco em **RDS PostgreSQL** (`store-db.c5wse42uq3ya.us-east-1.rds.amazonaws.com`), conexão por hostname interno da VPC. Redis roda como deployment dentro do mesmo cluster.

```
EKS store-cluster (us-east-1)
├── deployment/product           (1–5 pods, HPA CPU 50%)
│     └─ container cheqr/product:latest  (port 8080)
├── service/product              (ClusterIP)
├── configmap/product-configmap  (DATABASE_*, REDIS_*)
├── secret/product-secrets       (DATABASE_USERNAME/PASSWORD)
└── hpa/product
            ↓                     ↓
    RDS PostgreSQL          deployment/redis (in-cluster)
   schema: products
```
