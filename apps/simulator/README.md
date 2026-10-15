# HookScope Simulator

Aplicação de linha de comando da Fase 03 que gera cenários fictícios de pagamentos, comércio e logística e os envia ao endpoint de ingestão do HookScope.

## Configuração

| Variável | Obrigatória | Padrão | Regra |
| --- | --- | --- | --- |
| `HOOKSCOPE_API_KEY` | Sim | — | API Key criada para o projeto que receberá os eventos. |
| `HOOKSCOPE_API_URL` | Não | `http://localhost:8080` | URL HTTP(S) base da API. |
| `SIMULATOR_PROFILE` | Não | `demo` | Perfil `demo`, `steady` ou `burst`. |
| `SIMULATOR_INTERVAL_MS` | Não | Do perfil | Sobrescreve o intervalo com um valor de 100 a 60000 ms. |
| `SIMULATOR_EVENT_COUNT` | Não | `0` | Quantidade de eventos; `0` executa continuamente. |
| `SIMULATOR_REQUEST_TIMEOUT_MS` | Não | `5000` | Timeout HTTP de 100 a 60000 ms. |
| `SIMULATOR_FAILURE_RATE` | Não | `20` | Percentual de eventos com falha, entre 0 e 100. |
| `SIMULATOR_FAILURE_MODE` | Não | `mixed` | `mixed`, `error`, `timeout` ou `cancelled`. |

A chave é lida somente do ambiente e nunca deve ser versionada. As falhas simuladas continuam sendo eventos válidos: o status e a duração indicam o resultado da integração fictícia.

## Perfis de carga

| Perfil | Lote | Intervalo | Uso |
| --- | ---: | ---: | --- |
| `demo` | 1 evento | 1000 ms | Demonstração fácil de acompanhar. |
| `steady` | 1 evento | 250 ms | Fluxo contínuo de aproximadamente 4 eventos/s. |
| `burst` | 10 eventos concorrentes | 1000 ms | Pico de aproximadamente 10 eventos/s. |

## Executar no PowerShell

Com a API iniciada e uma API Key válida:

```powershell
cd apps/simulator
$env:HOOKSCOPE_API_KEY = 'cole-a-chave-gerada-aqui'
$env:SIMULATOR_PROFILE = 'burst'
$env:SIMULATOR_EVENT_COUNT = '50'
$env:SIMULATOR_FAILURE_RATE = '30'
$env:SIMULATOR_FAILURE_MODE = 'mixed'
npm start
```

Omita `SIMULATOR_EVENT_COUNT` ou use `0` para execução contínua. Pressione `Ctrl+C` para encerrar.

## Executar com Docker Compose

Com a API rodando no host e a API Key disponível na sessão:

```powershell
$env:HOOKSCOPE_API_KEY = 'cole-a-chave-gerada-aqui'
$env:SIMULATOR_PROFILE = 'burst'
$env:SIMULATOR_EVENT_COUNT = '50'

docker compose -f infra/compose.yaml --profile simulator up --build simulator
```

O serviço é opcional e não inicia no comando padrão do Compose. Dentro do contêiner, a API é acessada por `http://host.docker.internal:8080`; altere `SIMULATOR_DOCKER_API_URL` quando necessário. A imagem executa os testes durante o build e roda como usuário sem privilégios.

## Testar

```powershell
cd apps/simulator
npm test
```

O simulador usa apenas recursos nativos do Node.js 24 e não requer instalação de dependências.
