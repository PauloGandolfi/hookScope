# Autenticação e autorização — Fase 01

- O usuário entra com e-mail normalizado e senha. Senhas são armazenadas apenas como hash BCrypt com custo 12. Falhas de login sempre retornam `invalid_credentials`, sem indicar qual campo está incorreto.
- A autenticação web usa sessão do Spring Security. A resposta define `JSESSIONID` HttpOnly e SameSite=Lax; `SESSION_COOKIE_SECURE=true` deve ser configurado em produção com HTTPS. O ID da sessão é rotacionado no login. Logout invalida a sessão.
- O token CSRF fica na sessão. O frontend consulta `GET /api/v1/auth/csrf` e envia o valor recebido no cabeçalho `X-CSRF-TOKEN` em POST, PATCH e DELETE. O frontend não persiste o token nem a sessão em `localStorage`. Uma nova consulta obtém o token depois de expiração/logout.
- O Vite encaminha `/api` para o backend no desenvolvimento. Em produção, o proxy web deve manter `/api` no mesmo domínio, sem CORS global aberto.
- Endpoints de projetos e chaves exigem sessão de usuário. As consultas incluem `owner_id` ou verificam o projeto pertencente ao usuário antes de buscar chaves. Projetos de terceiros retornam 404.
- As API Keys têm 32 bytes aleatórios de segredo, além de prefixo aleatório. O banco armazena SHA-256 da chave completa, nunca o valor. A comparação usa `MessageDigest.isEqual`. O segredo só aparece na resposta da criação; o serviço de validação rejeita chaves expiradas e revogadas. API Keys não são usadas para acessar rotas administrativas nesta fase.
- Esta configuração assume uma única instância da API. Se houver múltiplas instâncias futuramente, a estratégia de sessões exigirá armazenamento compartilhado ou afinidade no balanceador.

Referências: [CSRF no Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [PasswordEncoder](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html) e [SameSite no Spring Boot](https://docs.spring.io/spring-boot/reference/web/servlet.html).
