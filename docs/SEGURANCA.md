# Checklist de segurança e LGPD

Legenda: ✅ implementado e testado · 🟡 implementado parcialmente · ⬜ pendente

## Autenticação e sessão
- ✅ Senhas com BCrypt (`DelegatingPasswordEncoder`, permite migrar de algoritmo sem reset)
- ✅ Política mínima de senha (10+ caracteres, letras e números, ≤ 72 bytes)
- ✅ Bloqueio temporário após N falhas (padrão 5 / 15 min, configurável) — `AuthIT`
- ✅ Mesma mensagem para e-mail inexistente e senha errada; tempo equalizado com hash fictício
- ✅ Access token JWT HS256 de 15 min mantido **só em memória** no navegador
- ✅ Refresh token opaco, rotativo, em cookie `HttpOnly; SameSite=Strict; Path=/api/v1/auth`, guardado como SHA-256
- ✅ Reuso de refresh token revogado revoga todas as sessões do usuário — `AuthIT`
- ✅ Endpoints por cookie exigem `X-TekCondo-Client` (força preflight CORS → anti-CSRF) — `AuthIT`
- ✅ Usuário/organização inativos não autenticam nem renovam sessão
- ✅ Segredo JWT obrigatório (≥ 32 bytes) fora do perfil dev; aplicação não sobe sem ele
- ✅ Cadastro de usuário por **convite**: ninguém além do titular conhece a senha — `UserManagementIT`
- ✅ Recuperação de senha por e-mail: token de uso único (hash SHA-256), 1 h de validade, no fragmento da URL (não vai a logs/Referer), resposta idêntica para e-mail inexistente, limite de 3 pedidos/hora — `PasswordIT`
- ✅ Redefinição encerra todas as sessões; troca de senha mantém só a sessão atual — `PasswordIT`
- ✅ Desativação de usuário tem efeito imediato (permissões lidas do banco) — `UserManagementIT`
- ✅ Administradora nunca fica sem administrador ativo; ninguém desativa a si mesmo — `UserManagementIT`
- ✅ Limite de tentativas por IP em login, esqueci e redefinir senha (429 + Retry-After; padrão 20 / 5 min) — `AuthRateLimitFilterTest`. Em memória, por instância: com várias instâncias, replicar o limite no proxy reverso
- ⬜ Limite de requisições por IP no restante da API
- ⬜ MFA opcional para administradores

## Autorização e multiempresa
- ✅ Permissões granulares (módulo + ação) agrupadas em papéis; escopo organização ou condomínio — `PermissionIT`
- ✅ Permissões lidas do banco a cada requisição (revogação imediata); nunca confiadas ao frontend
- ✅ `organization_id` vem apenas do token assinado; registros de outra organização → 404 — `TenantIsolationIT`
- ✅ FKs compostas impedem vínculo entre organizações no próprio banco — `TenantIsolationIT`
- ✅ Administrador da plataforma não acessa dados operacionais das administradoras — `PermissionIT`
- ⬜ Row-Level Security no PostgreSQL como segunda barreira

## Entrada, saída e transporte
- ✅ Bean Validation em todos os DTOs; erros RFC 9457 sem stack trace
- ✅ JPA com parâmetros (sem SQL concatenado); `LIKE` com escape de curingas
- ✅ Ordenação apenas por campos em lista branca — `CondominioIT`
- ✅ Entidades JPA nunca expostas diretamente (DTOs)
- ✅ Cabeçalhos: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, CSP no nginx
- ✅ Angular escapa interpolação por padrão (sem `innerHTML`); proteção a redirecionamento aberto no login
- ✅ CORS desligado por padrão (mesma origem via nginx); lista de origens por ambiente
- 🟡 HSTS/TLS: configuração documentada no `nginx.conf`, depende do ambiente de implantação

## Integridade financeira (novos controles)
- ✅ Fechamento mensal: nada retroativo (baixa, estorno, transferência, conciliação, lançamento) no período fechado; reabertura só com `FINANCE_CLOSE` e justificativa auditada — `PeriodClosingIT`
- ✅ Importação de extrato e de retorno idempotentes (hash do arquivo + chaves únicas no banco) — `StatementImportIT`, `BankReturnIT`
- ✅ Reservas sem sobreposição garantidas por restrição de exclusão no PostgreSQL — `ReservationIT`

## Arquivos e portal
- ✅ Upload valida extensão **e** assinatura real do conteúdo; limite de 10 MB; nome original sanitizado — `OperationsIT`
- ✅ Arquivos guardados com chave aleatória de 128 bits (nunca expostos); download apenas por endpoint autenticado, com `nosniff` e `no-store` — `OperationsIT`
- ✅ Morador acessa somente unidades com vínculo vigente; cobranças só para proprietário/responsável financeiro; anotações internas ocultas — `ReportsDashboardPortalIT`
- ✅ Exportação de relatórios auditada (`REPORT_EXPORTED`); CSV protegido contra injeção de fórmulas

## Auditoria e logs
- ✅ Trilha `audit_log` (somente inserção) para login, falhas, bloqueio, logout, cadastros, alterações (antes/depois), inativações
- ✅ Auditoria na mesma transação da operação
- ✅ Nunca registra senhas/tokens (`toString` dos DTOs de credenciais mascarado)
- ✅ Logs estruturados (ECS/JSON) em produção com `requestId`, `userId`, `organizationId`
- ✅ Auditoria de exportações e de downloads de documentos

## LGPD
- ✅ Minimização: só os dados necessários por cadastro; IP registrado apenas para segurança
- ✅ Ícones e fontes auto-hospedados (nenhum dado do usuário enviado a CDNs de terceiros)
- ⬜ Registro de bases legais e finalidades por tipo de dado (com o módulo de pessoas)
- ⬜ Exportação e anonimização de dados do titular (direitos do art. 18)
- 🟡 Política de retenção executada por job: registros de portaria anonimizados após `TEKCONDO_VISITOR_RETENTION_DAYS` (padrão 180) — `PrivacyRetentionJob`, `VisitorIT`. Demais dados (P12): pendente
- ✅ Portaria: documento do visitante exibido mascarado; código de acesso visível só para quem autorizou
- ⬜ Termo de uso / aviso de privacidade no portal do morador

## Operação
- ✅ E-mail via porta `MailGateway` (SMTP em produção; em dev grava em arquivo e não envia); envio só após o commit
- ✅ Segredos apenas por variáveis de ambiente (`.env.example` sem valores reais; `.env` no `.gitignore`)
- ✅ Container da API roda com usuário sem privilégios; healthcheck de readiness
- 🟡 Scripts `scripts/backup.sh` (banco + arquivos, soma SHA-256, retenção de 14) e `scripts/restore.sh` — agendar no servidor e testar restauração periodicamente
- ✅ CI (`.github/workflows/ci.yml`): testes, `npm audit --audit-level=high` a cada push; OWASP Dependency-Check semanal (requer `NVD_API_KEY`)
