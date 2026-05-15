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
| Banco | PostgreSQL (schema `products`) — RDS gerenciado em produção |
| Migrations | Flyway |
| Cache | Redis (implementado — ver [Bottlenecks](bottlenecks.md)) |
| Observabilidade | Spring Actuator + Micrometer Prometheus |
| Orquestração | Kubernetes (EKS), HPA target 50% CPU (1-5 réplicas) |
| CI/CD | Jenkins — Build → Push → Deploy to EKS |

## Status de entrega

| Atividade | Status |
|---|---|
| CRUD endpoints | ✅ |
| Bottleneck 1 — Cache Redis (3× speedup medido) | ✅ ([detalhes](bottlenecks.md)) |
| Bottleneck 2 — Métrica nativa de cache | ✅ ([detalhes](bottlenecks.md)) |
| k8s manifests + HPA | ✅ (`k8s/`) |
| Jenkinsfile com Build + Push + Deploy to EKS | ✅ |
| Deploy em cluster EKS real | ✅ (cluster `store-cluster` em `us-east-1`) |

## Repositórios do grupo

Ver [Repositórios](repositorios.md).
