# HookScope Simulator

Aplicação de linha de comando da Fase 03 que gera cenários fictícios de pagamentos, comércio e logística e os envia ao endpoint de ingestão do HookScope.

## Configuração

| Variável | Obrigatória | Padrão | Regra |
| --- | --- | --- | --- |
| `HOOKSCOPE_API_KEY` | Sim | — | API Key criada para o projeto que receberá os eventos. |
| `HOOKSCOPE_API_URL` | Não | `http://localhost:8080` | URL base da API. |
| `SIMULATOR_INTERVAL_MS` | Não | `1000` | Intervalo de 100 a 60000 ms. |
| `SIMULATOR_EVENT_COUNT` | Não | `0` | Quantidade de eventos; `0` executa continuamente. |
| `SIMULATOR_REQUEST_TIMEOUT_MS` | Não | `5000` | Timeout HTTP de 100 a 60000 ms. |

A chave é lida somente do ambiente e nunca deve ser versionada.

## Executar no PowerShell

Com a API iniciada e uma API Key válida:

```powershell
cd apps/simulator
$env:HOOKSCOPE_API_KEY = 'cole-a-chave-gerada-aqui'
$env:SIMULATOR_EVENT_COUNT = '10'
npm start
```

Omita `SIMULATOR_EVENT_COUNT` ou use `0` para execução contínua. Pressione `Ctrl+C` para encerrar.

## Testar

```powershell
cd apps/simulator
npm test
```

O simulador usa apenas recursos nativos do Node.js 24 e não requer instalação de dependências.
