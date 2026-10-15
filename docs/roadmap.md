# Roadmap do HookScope

| Fase | Entrega | Status |
| --- | --- | --- |
| 00 | Fundação e infraestrutura | Concluída |
| 01 | Autenticação, usuários, projetos e API Keys | Concluída |
| 02 | API de ingestão, persistência e consulta de eventos | Concluída |
| 03 | Aplicação simuladora de integrações | Concluída |
| 04 | Dashboard web de monitoramento | Próxima |
| 05 | SDK Java para instrumentação HTTP automática | Planejada |
| 06 | Métricas e analytics | Planejada |
| 07 | Alertas e notificações | Planejada |
| 08 | Segurança, performance e robustez | Planejada |
| 09 | Deploy, CI/CD e documentação final | Planejada |

As Fases 00–03 entregaram a fundação, identidade, projetos, gestão de API Keys, o ciclo completo de eventos e uma aplicação simuladora com perfis de carga. A próxima etapa é a Fase 04, com o dashboard web de monitoramento.

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

A Fase 03 foi concluída com:

- [x] Criar o núcleo executável com geração de cenários e envio autenticado.
- [x] Validar configuração, contrato dos eventos e comunicação HTTP com testes automatizados.
- [x] Adicionar perfis de carga e falhas controláveis.
- [x] Integrar o simulador ao ambiente Docker local.
- [x] Documentar o [fluxo completo de demonstração](local-demo.md).
