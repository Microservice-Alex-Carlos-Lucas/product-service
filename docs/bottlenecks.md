# Bottlenecks ✅ implementados

Carlos implementa **2 bottlenecks** no Product API. **Ambos foram implementados,
deployados via `docker compose` e validados end-to-end** — código nos arquivos abaixo.

| Bottleneck | Speedup medido |
|---|---|
| 1 — Cache `@Cacheable` + Redis | 3× (32ms → 12ms) |
| 2 — Métrica nativa de cache | qualitativo (`/actuator/caches/products` + `spring_data_repository_invocations_seconds`) |

---

## Bottleneck 1 — Cache de produto via `@Cacheable` + Redis

**Categoria:** Latência · *read-heavy endpoint*

### Problema

`order-service` chama `GET /products/{id}` via OpenFeign para cada item de cada pedido.
Cada pedido com N itens dispara N queries no Postgres. Sob carga, isso se torna o
gargalo principal do read path.

### Solução

- `pom.xml`: `spring-boot-starter-data-redis` + `spring-boot-starter-cache`
- `src/main/java/store/product/CacheConfig.java`: `@EnableCaching` +
  `RedisCacheManager` com TTL 60s, prefixo `products::`, serializador JSON
- `ProductService.get(UUID)`: `@Cacheable(value="products", key="#id")`
- `ProductService.create(...)`: `@CachePut(key="#result.id()")`
- `ProductService.delete(UUID)`: `@CacheEvict(key="#id")`

```java
@Cacheable(value = "products", key = "#id")
public ProductResponse get(UUID id) {
    return repository.findById(id)
            .map(ProductResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
}
```

### Verificação (medida em `docker compose`)

```bash
# 3 chamadas idênticas a /products/{id}
for i in 1 2 3; do curl -s -o /dev/null -H "$COOKIE" $BASE/products/$PID; done

# Inspeção do Redis confirma a key cacheada
docker run --rm --network store-api_default redis:7-alpine redis-cli -h redis KEYS '*'
# products::products::<uuid>

# Métrica do Spring Data Repository: só 1 findById foi executado
curl -s http://product:8080/actuator/prometheus | grep findById
# spring_data_repository_invocations_seconds_count{method="findById",repository="ProductRepository"} 1.0
```

**Latência medida:**
- 1ª chamada: 32ms (cache miss + DB)
- 2ª/3ª chamadas: 12ms (cache hit no Redis)
- **Speedup: 3×** + 2 queries no Postgres eliminadas.

---

## Bottleneck 2 — Métrica nativa de cache (zero código extra)

**Categoria:** Identificação de gargalos · *capacity planning*

### Problema

O ganho do Bottleneck 1 só é justificável se for **medido** e **observável** em produção.

### Solução

`application.yaml` habilita instrumentação nativa do Spring:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,caches
  metrics:
    cache:
      instrument: true
```

### Verificação

```bash
curl -s http://product:8080/actuator/caches
# {"cacheManagers":{"cacheManager":{"caches":{"products":{...}}}}}

curl -s http://product:8080/actuator/caches/products
# {"cacheManager":"cacheManager","name":"products",
#  "target":"org.springframework.data.redis.cache.DefaultRedisCacheWriter"}
```

`spring_data_repository_invocations_seconds_count{method="findById"}` no
`/actuator/prometheus` permite contrastar nº de queries no DB vs nº de hits no endpoint
— a diferença prova a eficácia do cache.

### O que isso desbloqueia

- Hit ratio em **Grafana** via
  `rate(cache_gets_total{result="hit"}[1m]) / rate(cache_gets_total[1m])`
- **Alertas** quando hit ratio < 80% (cache mal-dimensionado ou TTL curto demais)
- **HPA com métricas customizadas** via `prometheus-adapter`

---

## Arquivos relevantes

- `pom.xml` (Redis + Cache starters)
- `src/main/java/store/product/CacheConfig.java`
- `src/main/java/store/product/ProductService.java`
- `src/main/resources/application.yaml`
- `k8s/configmap.yaml` (REDIS_HOST/PORT)
- `k8s/deployment.yaml` (env vars)
- `k8s/hpa.yaml` (HPA CPU 50%, 1–5 réplicas)
