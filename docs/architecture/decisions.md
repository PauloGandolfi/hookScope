# Decisões arquiteturais — Fase 00

- **Monólito modular:** um processo Spring Boot. Os módulos de negócio futuros poderão separar domínio, aplicação e adaptadores quando houver casos de uso reais. Esta fase não cria portas ou interfaces vazias.
- **Persistência:** PostgreSQL 17. O Flyway é o único responsável pelo schema; Hibernate usa `ddl-auto=none`. A migration V1 inicializa `hookscope`, sem antecipar tabelas de negócio.
- **Configuração:** propriedades Spring com variáveis de ambiente. Os valores padrão são apenas para desenvolvimento local e correspondem ao Compose. Credenciais reais devem ficar fora do Git.
- **Saúde:** `/actuator/health` inclui o indicador do banco. A aplicação falha na inicialização se conexão ou migration falharem. Somente o endpoint de saúde é exposto pelo Actuator.
- **Web:** React e Vite separados do backend. Em desenvolvimento, o proxy do Vite encaminha `/actuator` para a API, sem abrir CORS global. Em produção, o servidor deve encaminhar esse caminho para a API no mesmo domínio.
- **Testes:** um teste de saúde sem banco roda em qualquer ambiente; o teste Testcontainers valida PostgreSQL e Flyway onde Docker estiver disponível.
