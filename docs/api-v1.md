# API HTTP v1 — Fase 01

Todas as rotas são relativas a `http://localhost:8080`. Corpos usam JSON. Erros retornam `{"code":"...","message":"..."}`. Rotas privadas requerem o cookie de sessão. POST, PATCH e DELETE requerem `X-CSRF-TOKEN`, obtido em `GET /api/v1/auth/csrf`.

| Método | Rota | Resultado |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Token CSRF da sessão |
| POST | `/api/v1/auth/register` | Conta criada (201) |
| POST | `/api/v1/auth/login` | Sessão autenticada (200) |
| POST | `/api/v1/auth/logout` | Sessão invalidada (204) |
| GET | `/api/v1/auth/me` | Usuário autenticado |
| POST | `/api/v1/projects` | Projeto criado (201) |
| GET | `/api/v1/projects?page=0&size=20` | Página dos projetos próprios (size máximo 100) |
| GET | `/api/v1/projects/{id}` | Detalhes do projeto próprio |
| PATCH | `/api/v1/projects/{id}` | Nome e/ou descrição atualizados |
| POST | `/api/v1/projects/{id}/api-keys` | Chave criada; segredo retornado uma única vez (201) |
| GET | `/api/v1/projects/{id}/api-keys` | Metadados das chaves, sem segredo |
| DELETE | `/api/v1/projects/{id}/api-keys/{keyId}` | Chave revogada (204) |

Exemplo em PowerShell, com valores fictícios. Execute as chamadas na mesma sessão para preservar o cookie. Substitua o ID do projeto retornado:

```powershell
$csrf = Invoke-RestMethod http://localhost:8080/api/v1/auth/csrf -SessionVariable webSession
$headers = @{ 'X-CSRF-TOKEN' = $csrf.token }
$register = @{ name = 'Ana Exemplo'; email = 'ana@example.test'; password = 'senha-ficticia-123' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/v1/auth/register -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $register
$login = @{ email = 'ana@example.test'; password = 'senha-ficticia-123' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/v1/auth/login -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $login
Invoke-RestMethod http://localhost:8080/api/v1/auth/me -WebSession $webSession
$project = @{ name = 'Integrações'; description = 'Exemplo local' } | ConvertTo-Json
$createdProject = Invoke-RestMethod http://localhost:8080/api/v1/projects -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $project
$key = @{ name = 'Ambiente de teste' } | ConvertTo-Json
$createdKey = Invoke-RestMethod "http://localhost:8080/api/v1/projects/$($createdProject.id)/api-keys" -Method Post -WebSession $webSession -Headers $headers -ContentType 'application/json' -Body $key
$createdKey.key # Copie agora: o valor não aparece em GET.
Invoke-RestMethod "http://localhost:8080/api/v1/projects/$($createdProject.id)/api-keys" -WebSession $webSession
Invoke-RestMethod "http://localhost:8080/api/v1/projects/$($createdProject.id)/api-keys/$($createdKey.apiKey.id)" -Method Delete -WebSession $webSession -Headers $headers
Invoke-RestMethod http://localhost:8080/api/v1/auth/logout -Method Post -WebSession $webSession -Headers $headers
```

Para editar o projeto, use `PATCH /api/v1/projects/{id}` com `{"name":"Novo nome","description":"Nova descrição"}`. Para listar, use `GET /api/v1/projects?page=0&size=20`. A resposta de criação de chave contém `apiKey` (metadados) e `key` (segredo); a listagem contém apenas metadados. `expiresAt` é opcional na criação e deve ser um instante ISO 8601 futuro. Chaves revogadas permanecem na listagem como histórico, mas não são válidas.
