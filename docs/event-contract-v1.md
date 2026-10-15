# Contrato de eventos v1 — Fase 02

Este documento define o contrato inicial de ingestão de eventos do HookScope. Ele é a referência para a migration V3, para o endpoint de ingestão e para os testes da Fase 02.

## Princípios

- O projeto não é informado no corpo. Ele é obtido da API Key usada na requisição.
- O produtor gera `eventId`; a combinação `(projectId, eventId)` identifica o evento de forma única.
- Datas usam ISO 8601 em UTC.
- O contrato aceita apenas os campos documentados. Campos desconhecidos retornam erro de validação.
- Metadados complementam o evento, mas não substituem campos pesquisáveis do contrato.

## Requisição

Endpoint planejado: `POST /api/v1/events`.

Autenticação planejada: `Authorization: Bearer <api-key>`. A API Key tem o formato já definido pelo HookScope (`hs_<prefixo>_<segredo>`). Essa rota não usa sessão nem CSRF.

```json
{
  "eventId": "018f4f9a-93f2-7f13-9d7d-7d7a3f58de21",
  "type": "payment.authorization",
  "status": "SUCCESS",
  "durationMs": 184,
  "occurredAt": "2026-10-15T14:32:41.583Z",
  "metadata": {
    "provider": "example-pay",
    "operation": "authorize",
    "httpStatus": 200,
    "retry": false
  }
}
```

## Campos

| Campo | Tipo | Obrigatório | Regras |
| --- | --- | --- | --- |
| `eventId` | UUID | Sim | Gerado pelo produtor. Aceita qualquer UUID válido e é idempotente dentro do projeto. |
| `type` | string | Sim | Entre 1 e 120 caracteres; formato `segmento.segmento`, em minúsculas, validado por `^[a-z][a-z0-9]*(\\.[a-z0-9]+)*$`. |
| `status` | enum | Sim | Um de `SUCCESS`, `ERROR`, `TIMEOUT` ou `CANCELLED`. |
| `durationMs` | inteiro | Sim | Duração em milissegundos, entre `0` e `86400000` (24 horas). |
| `occurredAt` | timestamp | Sim | ISO 8601 com offset; normalizado para UTC. Não pode estar mais de 5 minutos no futuro em relação ao servidor. |
| `metadata` | objeto JSON | Não | Padrão `{}`; máximo de 16 KiB em UTF-8 e 50 chaves no primeiro nível. Valores podem ser string, número, booleano, nulo, objeto ou array. |

## Campos definidos pelo servidor

| Campo | Origem | Finalidade |
| --- | --- | --- |
| `projectId` | API Key validada | Isolamento e consulta dos eventos do projeto. |
| `ingestedAt` | Relógio do servidor | Momento em que o HookScope aceitou o evento. |

Esses campos são persistidos e retornados nas consultas, mas não são aceitos no corpo de ingestão.

## Idempotência

A restrição única deve ser `(project_id, event_id)`.

- Primeiro envio válido: cria o evento e retorna `201 Created`.
- Reenvio com o mesmo `eventId` e conteúdo equivalente: não duplica o registro e retorna `200 OK`.
- Reenvio com o mesmo `eventId` e conteúdo diferente: retorna `409 Conflict` com o código `event_conflict`.

A equivalência considera `type`, `status`, `durationMs`, `occurredAt` normalizado e `metadata` como JSON estrutural, independentemente da ordem das chaves.

## Resposta de ingestão

```json
{
  "eventId": "018f4f9a-93f2-7f13-9d7d-7d7a3f58de21",
  "projectId": "74238f70-f0d8-44cf-80a9-20dd96ef9e90",
  "ingestedAt": "2026-10-15T14:32:41.721Z",
  "duplicate": false
}
```

Em um reenvio idempotente, `duplicate` é `true` e `ingestedAt` mantém o valor da primeira ingestão.

## Erros

Erros seguem o formato já adotado pela API:

```json
{
  "code": "invalid_event",
  "message": "Evento inválido.",
  "fieldErrors": {
    "durationMs": "deve ser maior ou igual a zero"
  }
}
```

| HTTP | Código | Situação |
| --- | --- | --- |
| `400` | `invalid_event` | JSON inválido ou violação das regras de campo. |
| `401` | `invalid_api_key` | Chave ausente, malformada, desconhecida, expirada ou revogada. |
| `409` | `event_conflict` | Mesmo `eventId` com conteúdo diferente no projeto. |
| `413` | `event_too_large` | Corpo ou metadados acima do limite permitido. |
| `429` | `rate_limit_exceeded` | Limite de ingestão excedido quando rate limiting for habilitado. |

## Segurança e privacidade

`metadata` não deve conter senhas, API Keys, tokens de sessão, dados completos de cartão ou outros segredos. A API deve rejeitar chaves de metadados evidentemente sensíveis (`password`, `authorization`, `apiKey`, `token`, `secret`) sem diferenciar maiúsculas e minúsculas.

## Fora do escopo deste contrato

- Ingestão em lote.
- Anexos ou payloads binários.
- Campos específicos de tracing distribuído.
- Taxa de retenção e arquivamento.
- Métricas agregadas e alertas.

Esses itens podem evoluir em versões futuras sem alterar o significado dos campos da v1.
