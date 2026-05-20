# API Reference

## Endpoints

!!! info "Autenticação"
    Todas as rotas (exceto `/actuator/*`) exigem o header `id-account`, injetado pelo gateway após validar o cookie `__store_jwt_token`. O serviço não valida JWT — confia no gateway.

---

### `POST /products`

Cria um produto novo.

**Headers:**

| Header | Descrição |
|--------|-----------|
| `id-account` | UUID da conta autenticada (injetado pelo gateway) |

**Body — `application/json`:**

```json
{
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

| Campo | Tipo | Validação |
|-------|------|-----------|
| `name` | string | `@NotBlank` |
| `price` | number | `@NotNull` · `@Positive` |
| `unit` | string | `@NotBlank` |

**Resposta 201 — Created:**

```json
{
  "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

O retorno popula o cache Redis via `@CachePut` — a próxima leitura por id é hit imediato.

---

### `GET /products`

Lista produtos. Aceita filtro opcional por nome (substring, case-insensitive).

**Query params:**

| Parâmetro | Tipo | Descrição |
|-----------|------|-----------|
| `name` | string (opcional) | Filtra `name ILIKE %name%` |

**Resposta 200:**

```json
[
  {
    "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
    "name": "Tomato",
    "price": 10.12,
    "unit": "kg"
  }
]
```

!!! note "Sem cache na listagem"
    Apenas `GET /products/{id}` é cacheado. A listagem vai sempre ao Postgres — manter consistência de filtros não vale o custo de invalidar cache de listas em todo `POST`/`DELETE`.

---

### `GET /products/{id}`

Retorna um produto por id. Caminho **cacheado** (TTL 60s) — ver [Bottlenecks](bottlenecks.md).

**Resposta 200:**

```json
{
  "id": "0195abfb-7074-73a9-9d26-b4b9fbaab0a8",
  "name": "Tomato",
  "price": 10.12,
  "unit": "kg"
}
```

**Erros:**

| Código | Motivo |
|--------|--------|
| `404` | Produto não encontrado |

---

### `DELETE /products/{id}`

Remove um produto e despeja a key correspondente do cache (`@CacheEvict`).

**Resposta 204 — No Content.**

| Código | Motivo |
|--------|--------|
| `404` | Produto não encontrado |

---

### `GET /actuator/health` · `GET /actuator/prometheus` · `GET /actuator/caches`

Endpoints do Spring Actuator (sem `id-account`):

- `health` — liveness/readiness usado por k8s.
- `prometheus` — métricas (`http_server_requests_*`, `spring_data_repository_invocations_seconds_*`, `cache_gets_total`, JVM, GC).
- `caches/products` — inspeção do cache Redis em uso.

## Modelo de erro

Habilitado `spring.mvc.problemdetails.enabled=true`. Respostas de erro seguem [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Product not found",
  "instance": "/products/0195abfb-7074-73a9-9d26-b4b9fbaab0a8"
}
```

## Exemplos

=== "cURL"

    ```bash
    # Login para obter o cookie
    curl -c cookies.txt -X POST http://localhost:8080/auth/login \
      -H "Content-Type: application/json" \
      -d '{"email":"user@example.com","password":"secret"}'

    # Criar produto
    curl -b cookies.txt -X POST http://localhost:8080/products \
      -H "Content-Type: application/json" \
      -d '{"name":"Tomato","price":10.12,"unit":"kg"}'

    # Buscar por id (segunda chamada vem do Redis)
    curl -b cookies.txt http://localhost:8080/products/0195abfb-7074-73a9-9d26-b4b9fbaab0a8

    # Listar filtrando por nome
    curl -b cookies.txt "http://localhost:8080/products?name=tom"

    # Deletar
    curl -b cookies.txt -X DELETE http://localhost:8080/products/0195abfb-7074-73a9-9d26-b4b9fbaab0a8
    ```

=== "Python"

    ```python
    import requests

    session = requests.Session()
    session.post("http://localhost:8080/auth/login", json={
        "email": "user@example.com",
        "password": "secret"
    })

    created = session.post("http://localhost:8080/products", json={
        "name": "Tomato",
        "price": 10.12,
        "unit": "kg"
    }).json()

    fetched = session.get(f"http://localhost:8080/products/{created['id']}").json()
    print(fetched)
    ```
