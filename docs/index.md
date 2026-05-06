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
| Bottleneck 1 — Cache Redis (3× speedup medido) | ✅ ([detalhes](bottlenecks.md)) |
| Bottleneck 2 — Métrica nativa de cache | ✅ ([detalhes](bottlenecks.md)) |
| k8s manifests + HPA | ✅ (`k8s/`) |
| Jenkinsfile com Build + Push + Deploy to EKS | ✅ |

## Repositórios do grupo

Ver [Repositórios](repositorios.md).
