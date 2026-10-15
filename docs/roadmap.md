# Roadmap do HookScope

| Fase | Entrega planejada |
| --- | --- |
| 00 | Fundação e infraestrutura |
| 01 | Autenticação, usuários, projetos e API Keys |
| 02 | API de ingestão, persistência e consulta de eventos |
| 03 | Aplicação simuladora de integrações |
| 04 | Dashboard web de monitoramento |
| 05 | SDK Java para instrumentação HTTP automática |
| 06 | Métricas e analytics |
| 07 | Alertas e notificações |
| 08 | Segurança, performance e robustez |
| 09 | Deploy, CI/CD e documentação final |

A Fase 00 preparou o ambiente. A Fase 01 implementou identidade, projetos e gestão de API Keys; a validação completa com PostgreSQL requer Docker.

## Fase 02 — eventos

A implementação da Fase 02 segue esta ordem:

1. Definir o [contrato de eventos v1](event-contract-v1.md).
2. Criar a migration V3 com persistência e índices de eventos.
3. Implementar a ingestão autenticada por API Key.
4. Implementar consultas paginadas e filtros por projeto.
5. Cobrir idempotência, autenticação, isolamento e validações com testes de integração.
6. Documentar os endpoints entregues.

O contrato v1 define os campos, limites, idempotência, respostas e erros esperados antes da implementação.
