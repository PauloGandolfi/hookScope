# HookScope

Plataforma para monitoramento de APIs e integrações. A Fase 01 adiciona contas, sessões, projetos e API Keys. A ingestão de eventos e o dashboard de métricas pertencem às próximas fases.

## Estrutura

| Caminho | Conteúdo |
| --- | --- |
| `apps/api` | Spring Boot e Maven Wrapper |
| `apps/web` | React, TypeScript e Vite |
| `infra/compose.yaml` | PostgreSQL local |
| `docs/architecture` | Decisões técnicas |
| `docs/roadmap.md` | Fases 00–09 |
| `.github/workflows` | Validação contínua |

`packages` e `examples` ficam reservados para fases futuras e não contêm arquivos artificiais.

## Versões e pré-requisitos

- JDK 21; Spring Boot **3.5.16**; Maven Wrapper **3.3.4** com Maven **3.9.16**.
- Node.js **24 LTS** e pnpm **11.25.0**; React **19.3.0**; TypeScript **6.0.2**; Vite **8.3.1**; ESLint **10.11.0**.
- Docker Desktop com Docker Compose; PostgreSQL **17** (imagem oficial).
- Git.

O Maven Wrapper baixa o Maven na primeira execução. O projeto compila com `--release 21`. As versões transitivas do backend são gerenciadas pelo Spring Boot.

## Iniciar no Windows PowerShell

Instale o pnpm uma vez com `npm install --global pnpm@11.25.0`. Na raiz deste clone, em três terminais:

```powershell
# Terminal 1: banco
docker compose -f infra/compose.yaml up -d --wait
```

```powershell
# Terminal 2: API
cd apps/api
.\mvnw.cmd spring-boot:run
```

```powershell
# Terminal 3: web
cd apps/web
pnpm install --frozen-lockfile
pnpm run dev
```

Abra `http://localhost:5173`. A página consulta `GET /actuator/health` pelo proxy do Vite. Com o banco e a API disponíveis, a resposta da API é `{"status":"UP"}` e a página mostra **API operacional**. Se a API não responder ou retornar estado diferente, a página mostra indisponibilidade. Consulte diretamente com:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Na primeira inicialização, o Flyway cria o schema `hookscope` e executa V1 e V2. A V2 cria `app_user`, `project` e `api_key`. O volume `postgres_data` preserva os dados entre reinícios. A página inicial agora apresenta cadastro/login; após entrar, você pode criar projetos e gerenciar API Keys.

### WSL/Linux

Use os mesmos comandos de Docker e pnpm. Para a API, execute `cd apps/api && ./mvnw spring-boot:run`; caso necessário, `chmod +x mvnw`. Para consultar o health, use `curl http://localhost:8080/actuator/health`.

## Portas e variáveis

| Serviço | Porta padrão | Configuração |
| --- | --- | --- |
| Web | 5173 | Vite em `127.0.0.1` |
| API | 8080 | `API_PORT` |
| PostgreSQL | 5432 | `POSTGRES_PORT`, publicado só em `127.0.0.1` |

O arquivo `.env.example` lista `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `API_PORT` e `SESSION_COOKIE_SECURE`. São exemplos **somente para desenvolvimento local**. Para alterar os padrões, na raiz do clone copie o exemplo para `.env`, ajuste os valores e carregue as variáveis na sessão PowerShell antes de entrar em `apps/api`; o Compose lê `.env` da raiz automaticamente. Exemplo:

```powershell
Copy-Item .env.example .env
# Edite .env sem versioná-lo. Depois, na sessão da API:
Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
```

Mantenha `DB_*` alinhados com `POSTGRES_*`. Se mudar `API_PORT`, ajuste o destino em `apps/web/vite.config.ts` ou use a porta padrão. Em produção, forneça segredos via ambiente, use HTTPS, defina `SESSION_COOKIE_SECURE=true` e encaminhe `/api` e `/actuator/health` para a API no mesmo domínio da web.

## Validação

```powershell
cd apps/api
.\mvnw.cmd verify
cd ../web
pnpm install --frozen-lockfile
pnpm run lint
pnpm run build
```

`HealthEndpointTest` e `ApiKeyMaterialTest` rodam sem Docker. `DatabaseIntegrationTest` e `PhaseOneIntegrationTest` usam Testcontainers para validar migrations, autenticação, CSRF, isolamento de projetos e chaves; são ignorados automaticamente se Docker não estiver disponível. No GitHub Actions, o runner executa ambos os jobs sem credenciais reais.

Para encerrar o banco: `docker compose -f infra/compose.yaml down`. Não use `down -v` se quiser preservar os dados.

Veja [a documentação da API](docs/api-v1.md), [a estratégia de autenticação](docs/architecture/authentication.md), [as decisões arquiteturais](docs/architecture/decisions.md) e [o roadmap](docs/roadmap.md).
