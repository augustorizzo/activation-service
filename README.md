# Activation Service

Serviço de ativação de produtos de clientes, desenvolvido como desafio técnico Java.

A aplicação registra solicitações, consulta seu estado e realiza uma chamada HTTP
a um provedor externo de ativação. O provedor é simulado com WireMock no ambiente
Docker Compose.

## 1. Tecnologias

- Java 21.
- Spring Boot 4.1.2.
- Spring MVC e Bean Validation.
- Spring Data JPA e Hibernate.
- PostgreSQL no ambiente Docker.
- H2 nos testes de persistência e API.
- Maven Wrapper.
- JUnit, Mockito, AssertJ e MockMvc.
- MockRestServiceServer nos testes do cliente HTTP.
- Docker e Docker Compose.
- Spring Boot Actuator.
- SLF4J e MDC para correlação de logs.

## 2. Como executar

### Pré-requisitos

Para executar o ambiente completo:

- Git.
- Docker com suporte a Docker Compose.
- Portas 8080 e 9090 disponíveis.
- Acesso à internet para baixar imagens e dependências no primeiro build.

Para executar os testes diretamente na máquina:

- JDK 21.
- JAVA_HOME configurado.

Não é necessário instalar Maven: o projeto contém Maven Wrapper.

### A partir de um clone limpo

Clone o repositório usando a URL fornecida na entrega e entre no diretório clonado.

Execute:

```shell
docker compose up -d --build --wait --wait-timeout 180
```

O Compose inicia:

| Serviço | Finalidade | Acesso local |
|---|---|---|
| app | API de ativação | http://localhost:8080 |
| db | PostgreSQL | Acesso pela rede interna do Compose |
| provider-mock | Provedor simulado | http://localhost:9090 |

O banco utiliza um volume nomeado para preservar os dados entre recriações
dos containers.

O Dockerfile executa `clean verify` durante a construção da aplicação.

### Verificar o ambiente

```shell
docker compose ps
docker compose logs --tail=80 app
```

Health check pelo PowerShell:

```powershell
Invoke-RestMethod `
    -Uri "http://localhost:8080/actuator/health" |
    ConvertTo-Json
```

### Executar testes

Windows PowerShell:

```powershell
.\mvnw.cmd clean verify
```

Linux/macOS:

```shell
sh ./mvnw clean verify
```

Essa execução compila, executa os testes e gera o JAR executável.

Para executar apenas os testes da API:

```powershell
.\mvnw.cmd -Dtest=ActivationRequestApiTest test
```

### Encerrar

```shell
docker compose down
```

Esse comando preserva o volume nomeado.

Atenção: o comando abaixo remove também o volume e apaga os dados locais:

```shell
docker compose down -v
```

## 3. API

### Criar solicitação

```http
POST /api/v1/requests
Content-Type: application/json
```

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

Validações dos três campos:

- Obrigatórios.
- Não aceitam string vazia ou composta apenas por espaços.
- Máximo de 100 caracteres.

O processamento é síncrono: a API aguarda a tentativa de ativação antes de responder.

Quando a solicitação é criada, a resposta é `201 Created`, com header `Location`
apontando para a consulta do recurso.

A resposta contém:

- id;
- externalId;
- customerId;
- productCode;
- status;
- createdAt;
- updatedAt.

As datas são representadas em UTC, no formato ISO-8601.

### Consultar solicitação

```http
GET /api/v1/requests/{id}
```

- `200 OK`: solicitação encontrada.
- `404 Not Found`: solicitação inexistente.
- `400 Bad Request`: identificador com formato inválido.

### Exemplo completo em PowerShell

```powershell
$body = @{
    externalId = "EXT-DEMO-" + [guid]::NewGuid().ToString()
    customerId = "CUST-10001"
    productCode = "INTERNET-500"
} | ConvertTo-Json

$created = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8080/api/v1/requests" `
    -ContentType "application/json" `
    -Body $body

$created | ConvertTo-Json

$savedId = $created.id

Invoke-RestMethod `
    -Uri "http://localhost:8080/api/v1/requests/$savedId" |
    ConvertTo-Json
```

O resultado da ativação depende da resposta configurada no provedor simulado.

## 4. Organização e decisões técnicas

### Separação de responsabilidades

- `api`: controllers, DTOs e tratamento de erros HTTP.
- `application`: coordenação do caso de uso e operações transacionais.
- `domain`: entidade, estados e regras de transição.
- `infrastructure`: persistência, cliente HTTP e filtro de correlação.

A aplicação depende da interface `ActivationProvider`; o cliente HTTP concreto
fica na infraestrutura.

A entidade de domínio também possui o mapeamento JPA. Para o escopo do desafio,
foi mantido um único modelo, sem duplicação entre entidade de persistência
e modelo de domínio.

DTOs de entrada e saída são separados da entidade.

### Estados

```text
PENDING → PROCESSING → COMPLETED
                     ↘ FAILED
```

A entidade inicia em `PENDING`.

As transições são realizadas por métodos de domínio:

- `startProcessing()`;
- `complete()`;
- `fail()`.

Os métodos verificam o estado anterior e rejeitam transições inválidas.
Não há setter público para alterar livremente o status.

No modelo atual, `COMPLETED` e `FAILED` são estados terminais.
Não existe reprocessamento automático de solicitações finalizadas.

A entidade possui `@Version` para controle otimista de concorrência.
Esse controle não substitui a restrição única de `externalId`.

### Duplicidade

O `externalId` possui a constraint única
`uk_activation_requests_external_id` no banco.

A consulta prévia permite identificar duplicidades já existentes, mas não
é a única proteção: duas requisições concorrentes podem consultar antes
que qualquer uma grave.

A restrição única impede a persistência de dois registros com o mesmo
`externalId`.

A política da API é:

| Cenário | Resposta |
|---|---|
| Mesmo externalId, mesmo conteúdo | 409 Conflict |
| Mesmo externalId, conteúdo diferente | 409 Conflict |
| Requisições concorrentes com o mesmo externalId | A constraint impede dois registros; a violação identificada é mapeada para 409 |

Essa escolha rejeita a reutilização do identificador, em vez de retornar
automaticamente o recurso existente em um retry.

Há testes de duplicidade sequencial pela API e diretamente no repository.
Não há teste concorrente com PostgreSQL/Testcontainers nesta versão.

### Integração com o provedor

A aplicação chama:

```http
POST /provider/v1/activate
```

O header `Idempotency-Key` recebe o `externalId`.

O envio dessa chave está testado. A garantia de idempotência no sistema externo
depende de o provedor implementar e respeitar esse contrato.

| Retorno | Resultado interno | Status da solicitação | Retry automático |
|---|---|---|---|
| HTTP 2xx | SUCCESS | COMPLETED | Não |
| HTTP 400 | PERMANENT_FAILURE | FAILED | Não |
| HTTP 500 | TEMPORARY_FAILURE | FAILED | Não |
| Timeout/falha de transporte capturada | UNKNOWN_OUTCOME | FAILED | Não |
| HTTP 429 ou 503 | TEMPORARY_FAILURE | FAILED | Não |

No código atual, os demais retornos fora de 2xx, 429 e 5xx são classificados
como `PERMANENT_FAILURE`.

A resposta `201` indica que a solicitação foi criada, não que a ativação
necessariamente teve sucesso. O resultado da tentativa é informado no campo
`status`.

#### Política de novas tentativas

Não há agendamento de retry nesta versão.

- HTTP 400 exige analisar e corrigir a causa antes de outra operação.
- HTTP 500, 429 e 503 podem justificar uma tentativa futura, mas ela não
  é executada automaticamente.
- Em timeout, é necessário reconciliar o resultado antes de repetir a chamada:
  o provedor pode ter concluído a ativação mesmo sem a resposta chegar.

Para uma evolução com retry, a mesma operação deveria preservar sua chave
de idempotência, respeitar o contrato do provedor e ter limite de tentativas.

Não se deve criar outro externalId apenas para contornar um timeout ambíguo.

#### Limitação do estado FAILED

Em `UNKNOWN_OUTCOME`, `FAILED` significa que este serviço não confirmou
a conclusão da tentativa. Não prova que o produto permaneceu desativado
no sistema externo.

O motivo da falha não é persistido separadamente nesta versão.
Uma evolução deve preservar essa classificação para apoiar suporte,
reconciliação e reprocessamento.

### Processamento síncrono e transações

O processamento síncrono mantém o fluxo simples e permite retornar o resultado
da tentativa na resposta de criação.

A contrapartida é que o tempo de resposta depende do banco e do provedor.

As operações de criação, início e conclusão possuem transações próprias
no serviço de persistência. O orquestrador não mantém uma transação de banco
aberta durante a chamada HTTP.

Essa separação não garante entrega após uma queda:

- O processo pode cair depois de gravar e antes de chamar o provedor.
- O processo pode cair depois da ativação externa e antes de persistir o resultado.

Não há recuperação automática dessas situações nesta versão.

Uma evolução usaria entrega durável, por exemplo outbox e worker, associada
a reconciliação e idempotência no provedor.

### Timeouts

O cliente HTTP recebe timeouts de conexão e leitura por configuração.

No Compose apresentado:

- Conexão: 1000 ms.
- Leitura: 2000 ms.

Esses limites não representam uma garantia de duração total do endpoint,
que também inclui processamento e acesso ao banco.

## 5. Configuração

Principais variáveis utilizadas no Compose:

| Variável | Finalidade |
|---|---|
| SPRING_DATASOURCE_URL | Endereço JDBC |
| SPRING_DATASOURCE_USERNAME | Usuário do banco |
| SPRING_DATASOURCE_PASSWORD | Senha do banco |
| SPRING_DATASOURCE_DRIVER_CLASS_NAME | Driver JDBC |
| SPRING_JPA_HIBERNATE_DDL_AUTO | Estratégia de atualização do schema |
| SPRING_JPA_OPEN_IN_VIEW | Controle de Open Session in View |
| PROVIDER_BASE_URL | Endereço do provedor |
| PROVIDER_CONNECT_TIMEOUT_MS | Timeout de conexão |
| PROVIDER_READ_TIMEOUT_MS | Timeout de leitura |
| JAVA_TOOL_OPTIONS | Opções da JVM |

As credenciais presentes no Compose são exclusivamente de demonstração local.
Não devem ser reutilizadas em produção.

O schema local utiliza `ddl-auto=update`.
Não há migrações versionadas nesta versão.

## 6. Tratamento de erros

O tratamento é centralizado em `ApiExceptionHandler`.

| Situação | HTTP |
|---|---|
| Dados inválidos ou requisição malformada | 400 |
| Solicitação inexistente | 404 |
| externalId duplicado | 409 |
| Erro inesperado | 500 |

As respostas utilizam `ProblemDetail`.
Erros de validação incluem uma lista com campo e mensagem.

Erros inesperados retornam mensagem genérica.
A exceção completa é registrada no servidor, não devolvida ao consumidor.

Falhas previstas do provedor são representadas pelo status da solicitação,
conforme a tabela de integração.

## 7. Segurança proposta

Autenticação e autorização não estão implementadas nesta versão.
A configuração atual é destinada a demonstração local e não deve ser exposta
publicamente como uma API protegida.

### Comunicação entre sistemas

A proposta é utilizar OAuth 2.0 com Client Credentials, pois o consumidor é
outro sistema, sem necessidade de uma sessão de usuário interativo.

Um provedor de identidade externo emitiria os access tokens.
Esta aplicação atuaria como Resource Server com Spring Security.

Não seria criado um servidor de autenticação dentro deste serviço.

### Validação do JWT

A API deveria validar:

- Assinatura com algoritmo permitido.
- Emissor esperado (`iss`).
- Audiência esperada para esta API (`aud`).
- Expiração (`exp`) e validade temporal, incluindo `nbf` quando presente.

As chaves públicas seriam obtidas pelo endpoint JWKS do provedor de identidade,
com rotação suportada pela configuração do Resource Server.

### Autorização

Escopos propostos:

| Operação | Escopo |
|---|---|
| Criar solicitação | requests.write |
| Consultar solicitação | requests.read |

Token ausente ou inválido deveria resultar em 401.
Token válido sem a permissão necessária deveria resultar em 403.

Escopo de leitura, sozinho, não garante isolamento entre consumidores.
Uma evolução persistiria a identidade do consumidor/tenant e restringiria
as consultas ao proprietário autorizado.

Essa identidade viria do token validado, não de um campo livre do payload.
O customerId não seria usado como prova de autorização.

### Credenciais do provedor

Caso o provedor exija autenticação, suas credenciais seriam obtidas de
um gerenciador de segredos, sem inclusão em código, imagem ou Git.

A rotação seguiria o mecanismo do provedor e permitiria atualização controlada,
com rastreabilidade e sem registrar valores sensíveis nos logs.

Também seriam aplicados HTTPS, limites de corpo e rate limiting no ponto
de entrada e/ou na aplicação, conforme a arquitetura adotada.

## 8. Estratégia de testes

### Domínio

`ActivationRequestTest`:

- Estado inicial.
- Transições válidas.
- Rejeição de transições inválidas.

São testes unitários sem contexto Spring.

### API

`ActivationRequestApiTest`:

- Criação e consulta.
- Validação e requisições malformadas.
- Duplicidade.
- Registro inexistente.
- Resultado final quando o provedor não confirma sucesso.
- Correlation ID recebido e gerado.

Utiliza contexto Spring, MockMvc, persistência H2 e provedor substituído
por mock Mockito.

Não é um teste ponta a ponta com o provedor HTTP real.

### Cliente HTTP

`HttpActivationProviderTest`:

- Método, URL, chave de idempotência e JSON enviados.
- Conversão de sucesso, 400, 500 e 429.
- Conversão de timeout simulado em resultado desconhecido.

Utiliza MockRestServiceServer, sem servidor externo.

O teste de timeout lança uma exceção simulada. Não valida o tempo real
de leitura configurado no cliente de produção.

### Persistência

`ActivationRequestRepositoryTest`:

- Gravação e consulta.
- ID, estado inicial e datas.
- Rejeição de externalId duplicado pela constraint.

Não há teste paralelo contra PostgreSQL nesta versão.

### Limitações

H2 permite testes rápidos, mas não substitui a validação das particularidades
do PostgreSQL.

Uma evolução adicionaria Testcontainers para concorrência, optimistic locking
e comportamento específico do banco, além de teste HTTP com atraso real
superior ao timeout.

Não há testes de segurança ou regras arquiteturais automatizadas nesta versão.

## 9. Observabilidade

Implementado:

- Spring Boot Actuator.
- Health check.
- Probes habilitadas para liveness e readiness.
- Header X-Correlation-Id recebido ou gerado pela aplicação.
- Correlation ID incluído no MDC.
- Log de entrada da requisição com método HTTP.
- Limpeza do MDC ao encerrar o filtro.

Os logs atuais são em texto, não JSON.

O identificador é devolvido no header de resposta.
Não há campo de correlação adicional no corpo do ProblemDetail.

O cliente HTTP ainda não propaga X-Correlation-Id ao provedor.
A chave Idempotency-Key tem finalidade diferente e não substitui essa propagação.

Não foram configurados nesta versão tracing distribuído, exportação Prometheus
ou métricas específicas de resultado e latência do provedor.

Exemplo em Windows PowerShell:

```powershell
$correlationId = [guid]::NewGuid().ToString()

$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "http://localhost:8080/actuator/health" `
    -Headers @{ "X-Correlation-Id" = $correlationId }

$response.Headers["X-Correlation-Id"]

docker compose logs --since=2m app |
    Select-String -SimpleMatch $correlationId
```

## 10. Cenário de análise de produção

Cenário fornecido:

- Tempo médio da API: 300 ms para 2,5 s.
- CPU: 45%.
- Memória: 60%.
- Algumas consultas: 50 ms para 1,2 s.
- Serviço externo: 400 ms para 3 s.

### 1. O que investigaria primeiro?

Confirmaria o início da degradação e sua relação com mudanças de versão,
configuração, infraestrutura, volume ou perfil de tráfego.

Separaria métricas por endpoint, instância, janela de tempo e resultado,
observando p50, p95 e p99, além da média.

Não concluiria que CPU e memória moderadas descartam gargalos:
o serviço pode estar aguardando rede, banco, locks ou conexões.

Também confirmaria se os números foram medidos na mesma população.
A média da API não precisa coincidir com a latência de uma amostra
de chamadas externas.

No projeto atual, verificaria ainda se os timeouts efetivos são os esperados:
o cenário hipotético de 3 s no provedor não deve ser confundido com
o read timeout de 2 s configurado no Compose local.

### 2. Como separaria aplicação, banco e integração?

Mediria o tempo de cada etapa:

- Espera e processamento HTTP.
- Aquisição de conexão do pool.
- Execução de SQL.
- Chamada ao provedor.
- Serialização e resposta.

Na aplicação, analisaria threads, filas, conexões, pausas de GC
e eventuais limites de CPU do container.

No banco, verificaria consultas mais custosas, locks, sessões, I/O,
plano de execução, estatísticas e volume de dados.

No provedor, observaria latência, erros, timeouts e alterações do contrato
ou de capacidade.

Utilizaria pg_stat_statements e EXPLAIN para apoiar a análise de consultas.
EXPLAIN ANALYZE seria aplicado com cuidado, pois executa a instrução.

### 3. Quais logs ou métricas seriam úteis?

- Taxa de requisições, erros e latência por endpoint.
- Chamadas ao provedor por resultado e latência.
- Quantidade de timeouts e falhas de transporte.
- Conexões ativas, ociosas e pendentes no pool.
- Tempo de aquisição de conexão.
- Duração e frequência de consultas.
- Locks e espera no PostgreSQL.
- Threads ocupadas e filas.
- GC, memória, CPU e throttling do container.
- Solicitações por status e tempo em PROCESSING.

Logs deveriam permitir correlação entre entrada, banco e integração,
registrando duração e resultado sem tokens, credenciais ou payloads sensíveis.

Essas métricas e logs detalhados são uma proposta de evolução:
não estão todos instrumentados nesta versão.

### 4. Quais melhorias consideraria?

A correção dependeria das evidências:

- Índices ou reescrita de SQL quando o plano justificar.
- Redução de consultas desnecessárias.
- Ajuste de capacidade e pools conforme os limites dos dependentes.
- Timeouts coerentes com o orçamento de latência.
- Redução de concorrência para proteger dependências saturadas.
- Processamento assíncrono e durável quando compatível com o contrato.
- Retry limitado apenas onde houver segurança e justificativa.

Não aumentaria indiscriminadamente pools, threads ou réplicas:
isso pode aumentar a pressão no banco e no provedor.

### 5. Poderia existir uma causa comum?

Uma hipótese seria aumento de concorrência ou tráfego pressionando
simultaneamente banco e provedor, elevando os tempos de espera.

Outra seria uma degradação de rede ou I/O compartilhado.

Confirmaria essas hipóteses pela correlação temporal de tráfego, latências,
filas, locks, conexões e métricas de infraestrutura.

Neste projeto, não atribuiria automaticamente locks no banco à chamada HTTP:
o fluxo apresentado não mantém uma transação aberta durante essa chamada.

### 6. Como validaria a correção?

Compararia antes e depois com carga e dados equivalentes, observando:

- p50, p95 e p99.
- Throughput e taxa de erros.
- Latência de banco e provedor.
- Uso de pools, filas e recursos.
- Quantidade de solicitações sem conclusão.
- Preservação da unicidade e das regras de estado.

Faria publicação gradual, com métricas de aceitação e possibilidade de rollback.
A melhoria da média isolada não seria suficiente para concluir que o problema
foi resolvido.

## 11. Execução em produção

O Dockerfile utiliza build multi-stage, JDK na compilação e JRE na imagem final.
A aplicação é executada com usuário não-root.

O Compose é um ambiente de demonstração local, não uma configuração completa
de produção.

Para produção:

1. Executar testes na pipeline.
2. Construir e analisar a imagem.
3. Publicar uma versão imutável no registro.
4. Executar com limites de recursos e configuração externa.
5. Usar PostgreSQL com persistência, backups e recuperação testada.
6. Substituir o mock pelo provedor real.
7. Obter credenciais por um gerenciador de segredos.
8. Habilitar autenticação/autorização e TLS.
9. Aplicar migrações versionadas antes de depender de mudanças de schema.
10. Configurar monitoramento, probes, encerramento controlado e rollback.

A configuração local usa MaxRAMPercentage=75.0 e limite de 512 MB.
Isso não é prova de dimensionamento adequado para produção.
O orçamento deve considerar heap e memória nativa e ser validado por carga.

O desligamento gracioso e o tempo de término do ambiente devem ser verificados
em teste específico; não foi apresentada essa validação nesta versão.

## 12. Microsserviços e cloud: proposta de evolução

### Múltiplas instâncias

As instâncias compartilhariam o banco e sua restrição única de externalId.

O controle de processamento exigiria coordenação durável, por exemplo
reivindicação de trabalho com controle de concorrência e idempotência externa.

A constraint única e @Version não fornecem, sozinhas, garantia de chamada única
ao provedor em todos os cenários de falha.

### Configuração e segredos

Configurações não sensíveis seriam fornecidas externamente.
Credenciais seriam armazenadas em um serviço de segredos com acesso mínimo,
auditoria e rotação.

### Eventos de conclusão

Uma evolução poderia publicar eventos de ativação concluída em fila ou tópico.

Uma outbox gravada na mesma transação da mudança de estado reduziria o risco
de atualizar o banco e perder a publicação.

Consumidores precisariam tolerar duplicatas e processar eventos de forma
idempotente.

### CI/CD

Proposta:

- Checkout e configuração do JDK.
- Build e testes com Maven Wrapper.
- Testes contra PostgreSQL.
- Análises de qualidade e segurança.
- Construção da imagem.
- Publicação no registro.
- Deploy gradual.
- Smoke tests e monitoramento.
- Rollback quando os critérios de aceitação não forem atingidos.

Não há pipeline de CI/CD apresentada nesta versão.

## 13. Pontos pendentes

- Autenticação e autorização implementadas.
- Isolamento por consumidor/tenant.
- Migrações versionadas.
- Retry durável, backoff e tratamento de Retry-After.
- Reconciliação de resultados desconhecidos e processamento interrompido.
- Persistência do motivo da falha.
- Testes concorrentes contra PostgreSQL.
- Teste de timeout com atraso HTTP real.
- Teste específico de erro inesperado e não exposição de detalhes.
- Validação de correlação em respostas de erro.
- Propagação do correlation ID ao provedor.
- Listagem paginada com filtros.
- Logs JSON, métricas de negócio e tracing.
- Pipeline CI/CD.
- Validação específica de desligamento gracioso.

Esses itens não devem ser interpretados como funcionalidades implementadas.

## 14. Melhorias futuras priorizadas

1. Segurança, isolamento e gestão de segredos antes de exposição pública.
2. Migrações e testes contra PostgreSQL.
3. Persistência dos resultados detalhados da integração.
4. Entrega durável, reconciliação e reprocessamento seguro.
5. Instrumentação de latência e resultados.
6. Pipeline automatizada e validação de carga.
7. Listagem paginada conforme necessidade de uso.

A proposta preserva uma solução simples e evita adicionar componentes
sem um requisito ou problema concreto.

## 15. Uso de IA

Foi utilizada assistência de IA para:

- Discutir estrutura e alternativas de implementação.
- Sugerir trechos de código e testes.
- Apoiar configurações de Docker, PostgreSQL e observabilidade.
- Investigar erros de execução e configuração.
- Comparar a implementação com o enunciado.
- Preparar esta documentação.

As sugestões foram aplicadas e verificadas durante o desenvolvimento,
com execução de testes e validações manuais apresentadas no projeto.

O uso de IA não substitui a responsabilidade do autor por compreender,
revisar e explicar a solução.