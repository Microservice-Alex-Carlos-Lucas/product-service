# Endpoints

| Método | Path | Descrição |
|---|---|---|
| `POST` | `/products` | Cria produto. Body: `{name, price, currency?}`. Retorna `201 Created` com `{id, name, price, currency}`. |
| `GET` | `/products` | Lista todos os produtos. |
| `GET` | `/products/{id}` | Retorna produto por id. `404` se não existir. |
| `PUT` | `/products/{id}` | Atualiza produto (substituição completa). |
| `DELETE` | `/products/{id}` | Remove produto. `204 No Content`. |
| `GET` | `/products/health-check` | Liveness/readiness — rota aberta no gateway. |
| `GET` | `/actuator/prometheus` | Métricas Prometheus (latência, GC, cache, etc.). |

## Headers obrigatórios (rotas autenticadas)

| Header | Origem | Descrição |
|---|---|---|
| `id-account` | injetado pelo gateway após validar JWT | id do usuário autenticado |

## Schema (Flyway)

| Migration | Conteúdo |
|---|---|
| `V1__create_products_table.sql` | `products(id UUID, name, price, currency)` |
