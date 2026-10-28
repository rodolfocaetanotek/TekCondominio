# Tek Condomínio — Gestão de Condomínios

Plataforma SaaS multiempresa para administradoras de condomínios.
**Backend:** Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · Flyway · PostgreSQL 16
**Frontend:** Angular 22 · Angular Material (M3) · Reactive Forms · RxJS/Signals

| Documento | Conteúdo |
|---|---|
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Requisitos, premissas de negócio, arquitetura, modelo de dados (ER), plano |
| [docs/PROGRESSO.md](docs/PROGRESSO.md) | Funcionalidades concluídas, testadas e pendentes |
| [docs/SEGURANCA.md](docs/SEGURANCA.md) | Checklist de segurança e LGPD |
| Swagger UI | `http://localhost:8080/swagger-ui.html` (com a API rodando) |

## Estrutura

```
tekcondo/
├── backend/            API Spring Boot (monólito modular)
│   └── src/main/resources/db/migration   migrações Flyway
├── frontend/           SPA Angular (+ nginx.conf para produção)
├── docs/               documentação
├── docker-compose.yml  PostgreSQL + API + frontend
└── .env.example        variáveis de ambiente (sem segredos)
```

## Opção A — Docker Compose (recomendado)

Requer Docker Desktop.

```bash
cp .env.example .env          # preencha TEKCONDO_DB_PASSWORD, TEKCONDO_JWT_SECRET e o admin de bootstrap
docker compose up -d --build
```

Acesse **http://localhost:8081** e entre com `TEKCONDO_BOOTSTRAP_ADMIN_EMAIL` / `TEKCONDO_BOOTSTRAP_ADMIN_PASSWORD`.

## Opção B — Desenvolvimento sem Docker

Requer JDK 21, Maven 3.9+ e Node 22+. (Nesta máquina, JDK e Maven portáteis ficam em `.tools/`.)

**1. API com PostgreSQL 16 embarcado** (binários reais, dados persistidos em `backend/.local-dev/`):

```bash
cd backend
mvn spring-boot:test-run -Dspring-boot.run.main-class=br.com.tekcondo.LocalDevApplication
```

Na primeira execução é criado o administrador da plataforma `admin@tekcondo.local` com senha aleatória gravada em
`backend/.local-dev/credentials.txt`. Para usar um PostgreSQL próprio, rode `mvn spring-boot:run` com
`TEKCONDO_DB_URL`, `TEKCONDO_DB_USER` e `TEKCONDO_DB_PASSWORD` definidos.

**2. Frontend** (proxy de `/api` para `localhost:8080`):

```bash
cd frontend
npm install
npm start          # http://localhost:4200
```

No PowerShell, para usar as ferramentas portáteis:

```powershell
$env:JAVA_HOME = "C:\Projetos\tekcondo\.tools\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;C:\Projetos\tekcondo\.tools\apache-maven-3.9.9\bin;$env:Path"
```

## Manual básico de utilização

**Perfis.** O *administrador da plataforma* cadastra administradoras. Cada administradora tem seus próprios
condomínios, usuários e dados (isolados das demais). O menu mostra apenas o que o perfil do usuário permite e o
**seletor de condomínio** na barra superior define em qual condomínio as telas operam.

1. **Plataforma** — menu *Administradoras* → *Nova administradora*. Na ficha da administradora é possível convidar
   administradores (recebem e-mail para definir a senha) e inativar/reativar a administradora.
2. **Usuários** — *Usuários* → *Novo usuário*: nome, e-mail e papel (toda a administradora ou um condomínio:
   síndico, subsíndico, conselheiro, funcionário). Em desenvolvimento os e-mails ficam em `backend/.local-dev/outbox/`.
3. **Cadastros** — *Condomínios* (dados, parâmetros); *Unidades* (blocos, unidades, fração ideal — a soma precisa
   fechar 100% para rateio por fração —, importação de planilha CSV com proprietários); *Pessoas* (CPF/CNPJ único,
   vínculos de proprietário/inquilino/morador com histórico, responsável financeiro e convite para o portal).
4. **Financeiro** — abas: *Contas a pagar* e *a receber* (lançamento único ou recorrente, aprovação, baixa parcial
   com multa/juros calculados, estorno e cancelamento com justificativa, anexos); *Contas e extrato* (saldos,
   extrato e conciliação); *Transferências*; *Orçamento* (previsto × realizado por conta e mês); *Configurações*
   (multa até 2%, juros, dia de vencimento, exigência de aprovação, centros de custo, plano de contas).
5. **Cobrança** — *Gerar cobranças*: escolha competência, conta e critério (fração ideal, igualitário, fixo ou
   consumo), **simule** e confirme. Aba *Inadimplência*: unidades em débito com valores atualizados; clique para ver o
   **demonstrativo de débitos**, registrar contatos e criar **acordo de parcelamento**.
6. **Fornecedores** e **Contratos** — vigência, reajuste e aviso prévio; contratos a vencer geram alerta.
7. **Ocorrências** — triagem (prioridade, responsável, prazo), andamento com mensagens internas ou visíveis ao
   morador, anexos e indicadores (pendentes, fora do prazo, tempo médio).
8. **Comunicados** — para todos, um bloco ou uma unidade; notificação interna e e-mail opcional; contagem de leitura.
9. **Assembleias** — crie (pauta: um item por linha; `#` = item informativo), *Convocar* (publica comunicado),
   *Iniciar*, registre presença/procurações e votos (peso por unidade ou fração ideal), deliberações com responsável
   e prazo, gere o **rascunho da ata** a partir dos dados e *Encerrar*.
10. **Documentos** — convenção, regulamento, atas etc.; *Interno* ou *Moradores* (aparece no portal).
11. **Relatórios** — 13 relatórios (receitas e despesas, balancete, fluxo de caixa, inadimplência, contas a pagar/
    receber, pagamentos, rateios, orçamento, movimentação bancária, despesas por fornecedor, ocorrências, contratos)
    com exportação **PDF, Excel e CSV**.
12. **Início** — painel com indicadores e gráfico dos últimos 12 meses, filtrável por condomínio e período.
13. **Portal do morador** — o morador entra com o convite recebido e vê suas unidades, cobranças (se proprietário ou
    responsável financeiro), comunicados, documentos, solicitações/ocorrências, assembleias/atas e agenda.
14. **Segurança da conta** — *Esqueci minha senha* no login; *Alterar senha* no menu do usuário; *Auditoria* registra
    as ações críticas.

## Backup e restauração (produção com Docker)

```bash
./scripts/backup.sh /caminho/backups          # banco (pg_dump) + arquivos + SHA-256; retenção de 14 cópias
./scripts/restore.sh /caminho/backups AAAAMMDD-HHMMSS
```

Agende o backup diário (cron), copie para fora do servidor e **teste a restauração** periodicamente.

## Testes

```bash
cd backend  && mvn test                    # 116 testes (unitários + integração com PostgreSQL real)
cd frontend && npx ng test --watch=false   # 15 testes (Vitest)
```

## Build para produção

```bash
cd backend  && mvn -B package              # target/tekcondo-backend-*.jar  (perfil: SPRING_PROFILES_ACTIVE=prod)
cd frontend && npx ng build                # dist/frontend/browser (servir com frontend/nginx.conf)
```

Em produção: TLS no proxy reverso, `TEKCONDO_COOKIE_SECURE=true`, HSTS habilitado no `nginx.conf`,
`TEKCONDO_SWAGGER_ENABLED=false` (se não for necessário), segredos em cofre/variáveis de ambiente,
e rotina de backup (ver [docs/SEGURANCA.md](docs/SEGURANCA.md)).
