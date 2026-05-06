# Product API

**Aluno:** Carlos
**Grupo:** Alex Chequer · Carlos · Lucas
**Disciplina:** Plataformas, Microserviços, DevOps e APIs — Insper 2026.1

---

## Sobre o serviço

Microsserviço de catálogo: cria, lê, atualiza e remove produtos. Cada
produto tem `id`, `name`, `price` e `currency`. O `order-service` o
consome via OpenFeign para resolver os itens de um pedido.

## Stack

| Item | Detalhe |
|---|---|
| Linguagem | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Banco | PostgreSQL (schema `products`) |
| Migrations | Flyway |
| Cache | Redis (a implementar — ver [Bottlenecks](bottlenecks.md)) |
| Observabilidade | Spring Actuator + Micrometer Prometheus |

## Status de entrega

| Atividade | Status |
|---|---|
| CRUD endpoints | ✅ |
| Bottleneck 1 — Cache Redis | ⏳ ([spec](bottlenecks.md)) |
| Bottleneck 2 — Métrica nativa de cache | ⏳ ([spec](bottlenecks.md)) |
| k8s manifests | ✅ (`k8s/`) |
| Jenkinsfile | ✅ |

## Repositórios do grupo

Ver [Repositórios](repositorios.md).
