# Demonstração local do HookScope

Este roteiro demonstra o fluxo completo entregue nas Fases 00–03: criar uma conta e um projeto, emitir uma API Key, produzir eventos com o simulador em Docker e consultar os eventos persistidos.

## 1. Iniciar a plataforma

Na raiz do repositório, inicie o PostgreSQL:

```powershell
docker compose -f infra/compose.yaml up -d --wait postgres
```

Em outro terminal, inicie a API:

```powershell
cd apps/api
.\mvnw.cmd spring-boot:run
```

Opcionalmente, inicie a interface web em um terceiro terminal:

```powershell
cd apps/web
pnpm install --frozen-lockfile
pnpm run dev
```

A API deve responder com `{"status":"UP"}` em `http://localhost:8080/actuator/health`.

## 2. Criar projeto e API Key

Siga o exemplo de autenticação e criação de projeto da [documentação da API](api-v1.md). Mantenha na sessão PowerShell as variáveis `$webSession`, `$createdProject` e `$createdKey`.

A API Key é exibida apenas na criação. Não a grave no Git.

## 3. Executar o simulador pelo Docker Compose

Na raiz do repositório, copie a chave para o ambiente da sessão e escolha o perfil:

```powershell
$env:HOOKSCOPE_API_KEY = $createdKey.key
$env:SIMULATOR_PROFILE = 'burst'
$env:SIMULATOR_EVENT_COUNT = '50'
$env:SIMULATOR_FAILURE_RATE = '30'
$env:SIMULATOR_FAILURE_MODE = 'mixed'

docker compose -f infra/compose.yaml --profile simulator up --build simulator
```

O serviço usa `host.docker.internal` para alcançar a API executada no host. A configuração também funciona no Docker Engine para Linux por meio do mapeamento `host-gateway`.

Cada linha concluída deve mostrar HTTP 201. O processo termina após a quantidade configurada; use `SIMULATOR_EVENT_COUNT=0` para execução contínua.

## 4. Consultar os eventos

Ainda na sessão PowerShell usada para criar o projeto:

```powershell
$projectId = $createdProject.id
Invoke-RestMethod "http://localhost:8080/api/v1/projects/$projectId/events?page=0&size=20" `
  -WebSession $webSession
```

Use os filtros descritos em [Consulta de eventos](api-v1.md#consulta-de-eventos) para verificar tipos e falhas específicos.

## 5. Encerrar

```powershell
docker compose -f infra/compose.yaml --profile simulator down
```

O volume `postgres_data` é preservado. Use `down -v` somente quando quiser apagar os dados locais.
