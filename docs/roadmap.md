# Roadmap do HookScope

| Fase | Entrega | Status |
| --- | --- | --- |
| 00 | Fundação e infraestrutura | Concluída |
| 01 | Autenticação, usuários, projetos e API Keys | Concluída |
| 02 | API de ingestão, persistência e consulta de eventos | Concluída |
| 03 | Aplicação simuladora de integrações | Em andamento |
| 04 | Dashboard web de monitoramento | Planejada |
| 05 | SDK Java para instrumentação HTTP automática | Planejada |
| 06 | Métricas e analytics | Planejada |
| 07 | Alertas e notificações | Planejada |
| 08 | Segurança, performance e robustez | Planejada |
| 09 | Deploy, CI/CD e documentação final | Planejada |

As Fases 00–02 entregaram a fundação, identidade, projetos, gestão de API Keys e o ciclo completo de ingestão e consulta de eventos. A Fase 03 está em andamento com uma aplicação simuladora que produz eventos reais para o HookScope.

## Fase 02 — eventos

A Fase 02 foi concluída com:

- [x] Definição do [contrato de eventos v1](event-contract-v1.md).
- [x] Migration V3 com persistência e índices de eventos.
- [x] Ingestão autenticada por API Key.
- [x] Consultas paginadas e filtros por projeto.
- [x] Testes de integração para idempotência, autenticação, isolamento e validações.
- [x] Documentação dos endpoints entregues.

A [documentação da API](api-v1.md) descreve os endpoints disponíveis e o contrato v1 registra os campos, limites, respostas e erros da ingestão.

## Fase 03 — simulador

A implementação da Fase 03 segue esta ordem:

- [x] Criar o núcleo executável com geração de cenários e envio autenticado.
- [x] Validar configuração, contrato dos eventos e comunicação HTTP com testes automatizados.
- [ ] Adicionar perfis de carga e falhas controláveis.
- [ ] Integrar o simulador ao ambiente Docker local.
- [ ] Documentar o fluxo completo de demonstração.
