# Bottlenecks

Carlos implementa **2 bottlenecks** no Product API. O spec detalhado
está em
[`docs/specs/product-bottlenecks.md`](https://microservice-alex-carlos-lucas.github.io/microservices/specs/product-bottlenecks/)
do repo do grupo.

## Bottleneck 1 — Cache de produto via `@Cacheable` + Redis

**Categoria:** Latência · *read-heavy endpoint*

`order-service` chama `GET /products/{id}` via Feign para cada item de
cada pedido. Cachear no Redis com TTL 60s reduz hits no Postgres em
~70% sob carga.

- pom: `spring-boot-starter-data-redis` + `spring-boot-starter-cache`
- `CacheConfig.java`: `@EnableCaching` + `RedisCacheManager`
- `ProductService.get(id)`: `@Cacheable(value="products", key="#id")`
- `update`/`delete`: `@CachePut` / `@CacheEvict`

**Verificação:** dois `GET /products/{id}` consecutivos — segundo deve
ser ≥6× mais rápido. Métricas em `/actuator/prometheus | grep cache_gets_total`.

## Bottleneck 2 — Métrica nativa de cache (zero código)

`management.metrics.cache.instrument: true` em `application.yaml` faz
o Spring expor automaticamente:

```
cache_gets_total{cache="products",result="hit"}
cache_gets_total{cache="products",result="miss"}
cache_puts_total{cache="products"}
```

Eficácia do cache em Grafana via:
`rate(cache_gets_total{result="hit"}[1m]) / rate(cache_gets_total[1m])`.

## Como medir antes/depois

`scripts/k6/product-cache.js` (no repo do grupo) — 200 VUs × 30s, loop
em 5 IDs. Comparar p95 de
`http_server_requests_seconds{uri="/products/{id}"}` antes e depois.
