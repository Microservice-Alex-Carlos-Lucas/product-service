# Bottlenecks implementados

A página de exercícios pede **ao menos 2 bottlenecks por integrante**. No
Product API entreguei **dois**, ambos rodando localmente via
`docker compose up -d --build product redis postgres` e também já
deployados no cluster EKS.

Ambos seguem a estrutura padrão do grupo: **Categoria → Problema → Solução
→ Verificação → O que isso desbloqueia → Arquivos relevantes**.

| Bottleneck | Speedup medido |
|---|---|
| 1 — Cache `@Cacheable` + Redis | 3× (32ms → 12ms) |
| 2 — Observabilidade nativa (Actuator + Micrometer Prometheus) | qualitativo (`/actuator/caches/products` + `spring_data_repository_invocations_seconds`) |

---

## Bottleneck 1 — Cache de produto com `@Cacheable` + Redis

**Categoria:** Latência · *read-heavy endpoint*

### Problema

`order-service` chama `GET /products/{id}` via OpenFeign para resolver
cada item de cada pedido. Um pedido com **N** itens dispara **N** queries
no Postgres em série. Quando o tráfego cresce, esse caminho vira o
gargalo principal do read path do catálogo:

- Cada `findById` paga round-trip TCP até o RDS (em outra AZ).
- O HPA escala pods de product, mas todos batem no **mesmo banco** — não
  adianta escalar horizontalmente se o gargalo está no DB.
- O catálogo é **read-heavy e raramente muda**: leituras dominam por
  ordens de magnitude.

### Solução

1. `pom.xml`: `spring-boot-starter-data-redis` + `spring-boot-starter-cache`.
2. `CacheConfig.java`: `@EnableCaching` + `RedisCacheManager` com TTL
   **60s**, prefixo `products::`, serializador JSON
   (`GenericJackson2JsonRedisSerializer`) — payload legível no
   `redis-cli`, robusto a mudanças de versão da JVM.
3. `ProductService` anotado para manter o cache **consistente** em todas
   as operações:

```java
@CachePut(value = "products", key = "#result.id()")
public ProductResponse create(ProductRequest request) {
    Product product = new Product(null, request.name(), request.price(), request.unit());
    return ProductResponse.from(repository.save(product));
}

@Cacheable(value = "products", key = "#id")
public ProductResponse get(UUID id) {
    return repository.findById(id)
            .map(ProductResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
}

@CacheEvict(value = "products", key = "#id")
public void delete(UUID id) {
    if (!repository.existsById(id)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
    }
    repository.deleteById(id);
}
```

- **`@Cacheable`** no `get` — primeira leitura popula o cache, próximas
  servem do Redis.
- **`@CachePut`** no `create` — o produto recém-criado já entra no cache
  pelo `id` gerado, evitando o miss da primeira leitura.
- **`@CacheEvict`** no `delete` — remove a key correspondente para que
  nenhum pod sirva produto deletado.

> Listagem (`/products`) **não** é cacheada de propósito: invalidar lista
> em todo `POST`/`DELETE` (com filtro por nome) não compensa, e leituras
> de detalhes (`/products/{id}`) já dominam o volume.

### Verificação (medida em `docker compose`)

```bash
# Cria produto, captura id
PID=$(curl -sb cookies.txt -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Tomato","price":10.12,"unit":"kg"}' | jq -r .id)

# 3 chamadas idênticas a /products/{id}
for i in 1 2 3; do
  curl -sb cookies.txt -o /dev/null -w "call $i: %{time_total}s\n" \
    http://localhost:8080/products/$PID
done
```

```text
call 1: 0.032s   (cache miss → Postgres + Redis SET)
call 2: 0.012s   (cache hit no Redis)
call 3: 0.012s   (cache hit no Redis)
```

Inspeção do Redis confirma a key cacheada:

```bash
docker exec -it product-service-redis-1 redis-cli KEYS '*'
# 1) "products::products::0195abfb-7074-73a9-9d26-b4b9fbaab0a8"
```

E a métrica do Spring Data Repository prova que só **1** `findById` foi
para o banco apesar das 3 requisições HTTP:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep findById
# spring_data_repository_invocations_seconds_count{method="findById",repository="ProductRepository"} 1.0
```

**Resultado:**

- 1ª chamada: 32ms (cache miss + DB)
- 2ª/3ª chamadas: 12ms (cache hit no Redis)
- **Speedup: 3×** + 2 queries no Postgres eliminadas a cada janela de 60s.

### O que isso desbloqueia

- **Order Service ganha latência previsível**: criar um pedido com N
  itens vira N leituras no Redis (~1ms cada) ao invés de N round-trips
  Postgres (~10–30ms cada).
- **HPA do Kubernetes pode escalar product sem multiplicar carga no
  RDS** — o cache absorve a maior parte do tráfego de leitura, e o banco
  só vê writes + invalidações.
- **Cache compartilhado entre réplicas**: `DELETE` em qualquer pod
  invalida a key globalmente; `POST` aquece a key para todos. Isso seria
  impossível com cache local (Caffeine) sem um broker pub/sub.

### Arquivos relevantes

- `pom.xml` (linhas com `spring-boot-starter-data-redis` e
  `spring-boot-starter-cache`)
- `src/main/java/store/product/CacheConfig.java` (RedisCacheManager
  TTL 60s, prefix, JSON serializer)
- `src/main/java/store/product/ProductService.java`
  (`@Cacheable` / `@CachePut` / `@CacheEvict`)
- `src/main/resources/application.yaml` (`spring.cache.type=redis`,
  `spring.data.redis.*`)
- `k8s/configmap.yaml` (`REDIS_HOST`, `REDIS_PORT`)
- `k8s/deployment.yaml` (envFrom configmap)

---

## Bottleneck 2 — Observabilidade com Actuator + Micrometer Prometheus

**Categoria:** Identificação de gargalos · *capacity planning*

### Problema

Sem métricas, é impossível:

- Saber se a latência percebida pelo `order-service` vem do JPA, do
  Redis, da rede até o RDS, ou do próprio Tomcat.
- Justificar quantitativamente o ganho do cache (Bottleneck 1) — quantos
  hits/misses por segundo? qual o hit ratio em produção?
- Calibrar o **HPA**: 50% de CPU é o limiar certo? a CPU é mesmo o
  recurso saturando primeiro?

Observabilidade também é pré-requisito para alertas e para o
`prometheus-adapter` (HPA com métricas customizadas).

### Solução

Stack Spring Boot já traz tudo "grátis", precisa **habilitar e expor**:

1. `pom.xml`: `spring-boot-starter-actuator` + `micrometer-registry-prometheus`.
2. `application.yaml`:

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

   - `include: prometheus` expõe `/actuator/prometheus` no formato OpenMetrics.
   - `include: caches` expõe `/actuator/caches/products` para inspeção
     ao vivo do `RedisCacheManager`.
   - `metrics.cache.instrument: true` plumba o `CacheManager` no
     Micrometer, gerando `cache_gets_total{result="hit|miss"}`.

3. Métrica de repositório (`spring_data_repository_invocations_seconds`)
   vem habilitada por default no Spring Boot 4 — permite contrastar nº
   de queries no DB vs nº de hits no endpoint HTTP.

### Verificação

```bash
curl -s http://localhost:8080/actuator/caches
# {"cacheManagers":{"cacheManager":{"caches":{"products":{...}}}}}

curl -s http://localhost:8080/actuator/caches/products
# {"cacheManager":"cacheManager","name":"products",
#  "target":"org.springframework.data.redis.cache.DefaultRedisCacheWriter"}

curl -s http://localhost:8080/actuator/prometheus | grep -E "^(http_server_requests|cache_gets|spring_data_repository)"
# http_server_requests_seconds_count{method="GET",uri="/products/{id}",status="200"} 3.0
# http_server_requests_seconds_sum{method="GET",uri="/products/{id}",status="200"} 0.056
# cache_gets_total{cache="products",result="hit"} 2.0
# cache_gets_total{cache="products",result="miss"} 1.0
# spring_data_repository_invocations_seconds_count{method="findById"} 1.0
```

A query Prometheus
`rate(cache_gets_total{result="hit"}[1m]) / rate(cache_gets_total[1m])`
dá a **eficácia do cache** ao vivo em painéis Grafana.

### O que isso desbloqueia

- **Dashboards Grafana** com p95/p99 por rota, taxa de erro, hit ratio
  do cache, e latência do RDS isolada do Redis.
- **Alertas Prometheus**:
    - `rate(cache_gets_total{result="hit"}[5m]) / rate(cache_gets_total[5m]) < 0.8`
      → cache mal-dimensionado ou TTL curto demais.
    - `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1`
      → erro 5xx anormal.
- **HPA com métricas customizadas** via `prometheus-adapter`: escalar
  pods quando `http_server_requests_seconds_count` exceder X req/s, sem
  depender só de CPU.

### Arquivos relevantes

- `pom.xml` (`spring-boot-starter-actuator`,
  `micrometer-registry-prometheus`)
- `src/main/resources/application.yaml` (`management.endpoints.web.exposure.include`,
  `management.metrics.cache.instrument`)
- `k8s/hpa.yaml` (HPA CPU 50%, 1–5 réplicas — alvo natural para evoluir
  para métricas customizadas)
