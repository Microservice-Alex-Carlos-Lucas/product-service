# Desenvolvimento

## Pré-requisitos

- JDK 25 (Eclipse Temurin recomendado)
- Maven 3.9+ (ou usar o wrapper `./mvnw`)
- Docker + Docker Compose (para subir Postgres e Redis integrados)

## Setup local

```bash
git clone https://github.com/Microservice-Alex-Carlos-Lucas/product-service.git
cd product-service

# Build (gera target/product-service-1.0.0.jar)
./mvnw clean package -DskipTests
```

### Subir dependências (Postgres + Redis)

A maneira mais rápida é usar o `compose.yaml` do repositório
[`microservices`](https://github.com/Microservice-Alex-Carlos-Lucas/microservices) — ele já levanta
todos os serviços integrados. Para rodar **apenas** o product
isoladamente:

```bash
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=store \
  -e POSTGRES_USER=store \
  -e POSTGRES_PASSWORD=store \
  postgres:16

docker run -d --name redis -p 6379:6379 redis:7-alpine

# O schema 'products' é criado pelo Flyway no startup
```

### Rodar o servidor localmente

```bash
DATABASE_HOST=localhost \
DATABASE_PORT=5432 \
DATABASE_DB=store \
DATABASE_USERNAME=store \
DATABASE_PASSWORD=store \
REDIS_HOST=localhost \
REDIS_PORT=6379 \
./mvnw spring-boot:run
```

O servidor sobe em `http://localhost:8080`.

!!! note
    Rodando localmente (fora do gateway), os endpoints exigem o header `id-account` enviado manualmente:
    ```bash
    curl -H "id-account: 00000000-0000-0000-0000-000000000000" http://localhost:8080/products
    ```

## Variáveis de ambiente

| Variável | Default | Descrição |
|----------|---------|-----------|
| `DATABASE_HOST` | — | Hostname do Postgres |
| `DATABASE_PORT` | — | Porta do Postgres |
| `DATABASE_DB` | — | Nome do banco |
| `DATABASE_USERNAME` | — | Usuário com acesso ao schema `products` |
| `DATABASE_PASSWORD` | — | Senha do usuário |
| `REDIS_HOST` | `redis` | Hostname do Redis |
| `REDIS_PORT` | `6379` | Porta do Redis |

Em produção (EKS), `DATABASE_*` vem do `product-secrets` e `REDIS_*` do
`product-configmap`.

## Testes

```bash
./mvnw test
```

Os testes usam `spring-boot-starter-test` (JUnit 5 + Mockito +
AssertJ + Spring Test).

## Docker

```bash
# 1. Build do jar (necessário, o Dockerfile copia target/*.jar)
./mvnw clean package -DskipTests

# 2. Build da imagem
docker build -t product-service:local .

# 3. Rodar
docker run -p 8080:8080 \
  -e DATABASE_HOST=host.docker.internal \
  -e DATABASE_PORT=5432 \
  -e DATABASE_DB=store \
  -e DATABASE_USERNAME=store \
  -e DATABASE_PASSWORD=store \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  product-service:local
```

### Integrado com a plataforma

No `compose.yaml` da plataforma já existe a entrada `product` (e o
`gateway-service/application.yaml` já roteia `/products/**`). Basta:

```bash
docker compose up -d --build product redis postgres
```

## CI/CD — Jenkins

O `Jenkinsfile` roda 3 estágios:

```groovy
pipeline {
    agent any
    environment {
        SERVICE = 'product'
        NAME    = "cheqr/${env.SERVICE}"
    }
    stages {
        stage('Build')              { /* mvn -B -DskipTests clean package        */ }
        stage('Build & Push Image') { /* docker buildx multi-arch (amd64+arm64)  */ }
        stage('Deploy to EKS')      { /* aws eks update-kubeconfig + kubectl set image */ }
    }
}
```

Credenciais e variáveis usadas:

| Item | Origem |
|------|--------|
| `dockerhub-credential` | Jenkins credential (username + token) |
| `AWS_REGION` | env do Jenkins (`us-east-1`) |
| `EKS_CLUSTER_NAME` | env do Jenkins (`store-cluster`) |

Cada build gera as tags `cheqr/product:latest` e `cheqr/product:${BUILD_ID}`,
faz `kubectl set image deployment/product product=cheqr/product:${BUILD_ID}`
e aguarda `rollout status` (timeout 120s).

## Deploy manual no EKS

```bash
aws eks update-kubeconfig --region us-east-1 --name store-cluster

kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

kubectl rollout status deployment/product
kubectl get hpa product
```

## Documentação local

```bash
pip install -r docs-requirements.txt
mkdocs serve
```

A documentação fica disponível em `http://localhost:8000`.

### Publicação (GitHub Pages)

O workflow `.github/workflows/docs.yml` faz deploy automático para
`gh-pages` a cada push na `main`. Site publicado em
[microservice-alex-carlos-lucas.github.io/product-service](https://microservice-alex-carlos-lucas.github.io/product-service/).
