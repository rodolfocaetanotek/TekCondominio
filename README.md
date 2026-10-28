# Tek Condomínio — Gestão de Condomínios

Plataforma SaaS multiempresa para administradoras de condomínios.
**Backend:** Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA/Hibernate · Flyway · PostgreSQL 16
**Frontend:** Angular 22 · Angular Material (M3) · Reactive Forms · RxJS/Signals

| Documento | Conteúdo |
|---|---|
| [docs/ARQUITETURA.md](docs/ARQUITETURA.md) | Requisitos, premissas de negócio, arquitetura, modelo de dados (ER), plano |
| [docs/PROGRESSO.md](docs/PROGRESSO.md) | Funcionalidades concluídas, testadas e pendentes |
| [docs/SEGURANCA.md](docs/SEGURANCA.md) | Checklist de segurança e LGPD |
| [docs/AUDITORIA.md](docs/AUDITORIA.md) | Auditoria técnica: estado encontrado, riscos e pontos fortes |
| [docs/BENCHMARK.md](docs/BENCHMARK.md) | Pesquisa de concorrentes (Sibrax, Superlógica, uCondo, TownSq, Group) com fontes e datas |
| [docs/MATRIZ-COMPARATIVA.md](docs/MATRIZ-COMPARATIVA.md) | Matriz de funcionalidades, lacunas priorizadas e backlog técnico |
| [docs/INTEGRACOES.md](docs/INTEGRACOES.md) | OFX, CNAB 240, boleto/PIX e demais dependências externas |
| [docs/RELATORIO-FINAL.md](docs/RELATORIO-FINAL.md) | O que foi implementado nesta etapa, testes, limitações e próximos passos |
| [docs/UI-PADRAO.md](docs/UI-PADRAO.md) | Padrão visual (cores, tipografia, menu, componentes, celular, acessibilidade) |
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
   extrato e conciliação manual); *Conciliação (OFX)* (importe o extrato do banco: o sistema concilia sozinho os pares
   inequívocos e sugere os demais; tarifas e rendimentos podem ser lançados direto da linha); *Transferências*;
   *Orçamento* (previsto × realizado); *Indicadores* (projeção de caixa pelos vencimentos e despesas fora do padrão);
   *Fechamento mensal* (confere pendências, fecha o mês com fotografia dos saldos e bloqueia alterações retroativas;
   reabertura só pelo administrador, com justificativa); *Configurações* (multa até 2%, juros, dia de vencimento,
   exigência de aprovação, centros de custo, plano de contas).
5. **Cobrança** — *Gerar cobranças*: escolha competência, conta e critério (fração ideal, igualitário, fixo ou
   consumo), **simule** e confirme. Aba *Inadimplência*: unidades em débito com valores atualizados; clique para ver o
   **demonstrativo de débitos**, registrar contatos e criar **acordo de parcelamento**. Aba *Retorno bancário*:
   importe o arquivo de retorno **CNAB 240** do banco para baixar automaticamente as cobranças pagas (identificadas
   pelo **número de cobrança** de cada título, que deve ir no "seu número" do boleto — ver docs/INTEGRACOES.md).
6. **Fornecedores** e **Contratos** — vigência, reajuste e aviso prévio; contratos a vencer geram alerta.
7. **Ocorrências** — triagem (prioridade, responsável, prazo), andamento com mensagens internas ou visíveis ao
   morador, anexos e indicadores (pendentes, fora do prazo, tempo médio).
8. **Comunicados** — para todos, um bloco ou uma unidade; notificação interna e e-mail opcional; contagem de leitura.
9. **Assembleias** — crie (pauta: um item por linha; `#` = item informativo), *Convocar* (publica comunicado),
   *Iniciar*, registre presença/procurações e votos (peso por unidade ou fração ideal), deliberações com responsável
   e prazo, gere o **rascunho da ata** a partir dos dados e *Encerrar*.
9a. **Reservas** — cadastre as áreas comuns (horário, duração máxima, antecedência, limite mensal por unidade, taxa,
   aprovação prévia, bloqueio de inadimplentes); aprove/recuse/cancele reservas. A taxa vira cobrança na unidade.
9b. **Portaria** — libere a entrada pelo código de 6 dígitos gerado pelo morador, registre chegadas sem aviso e as
   saídas. O morador é notificado; documentos aparecem mascarados e os registros são anonimizados após 180 dias.
10. **Documentos** — convenção, regulamento, atas etc.; *Interno* ou *Moradores* (aparece no portal).
11. **Relatórios** — 13 relatórios (receitas e despesas, balancete, fluxo de caixa, inadimplência, contas a pagar/
    receber, pagamentos, rateios, orçamento, movimentação bancária, despesas por fornecedor, ocorrências, contratos)
    com exportação **PDF, Excel e CSV**.
12. **Início** — painel com indicadores e gráfico dos últimos 12 meses, filtrável por condomínio e período.
13. **Portal do morador** — o morador entra com o convite recebido e vê suas unidades, cobranças (se proprietário ou
    responsável financeiro), comunicados, documentos, solicitações/ocorrências, **reservas**, **autorização de
    visitantes** (gera o código para a portaria), assembleias/atas e agenda.
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
cd backend  && mvn test                    # 141 testes (unitários + integração com PostgreSQL real)
cd frontend && npx ng test --watch=false   # 23 testes (Vitest)
```

**Validação ponta a ponta** contra a API em execução (cria uma administradora nova e percorre condomínio → rateio →
retorno CNAB → OFX → inadimplência/acordo → fechamento → reservas → portaria → portal → isolamento):

```bash
# instância isolada, sem tocar nos dados de .local-dev
cd backend && mvn spring-boot:test-run -Dspring-boot.run.main-class=br.com.tekcondo.LocalDevApplication \
  "-Dspring-boot.run.jvmArguments=-Dtekcondo.localdev.dir=.local-dev-e2e -Dtekcondo.localdev.pg-port=54339 -Dserver.port=8090"
python scripts/e2e_validation.py http://localhost:8090 backend/.local-dev-e2e     # 19 verificações
```

## Versionamento e integração contínua

O projeto está em git (branch `main`). O primeiro commit guarda o estado anterior à etapa de benchmarking, para
comparação (`git diff HEAD~1 --stat`).

O workflow `.github/workflows/ci.yml` roda no GitHub Actions a cada push ou pull request:

| Job | O que verifica |
|---|---|
| `backend` | `mvn test` (unitários + integração com PostgreSQL 16 real embarcado) e `mvn package` |
| `frontend` | `npm ci`, `ng test`, `ng build` de produção e `npm audit --audit-level=high` |
| `dependency-check` | OWASP Dependency-Check nas dependências Java (semanal ou manual). Falha com CVSS ≥ 7. Requer o segredo `NVD_API_KEY` (chave gratuita em nvd.nist.gov); sem ele, o job é ignorado com aviso. |

Para ativar: crie o repositório remoto, `git remote add origin <url>` e `git push -u origin main`.

## Build para produção

```bash
cd backend  && mvn -B package              # target/tekcondo-backend-*.jar  (perfil: SPRING_PROFILES_ACTIVE=prod)
cd frontend && npx ng build                # dist/frontend/browser (servir com frontend/nginx.conf)
```

Em produção: TLS no proxy reverso, `TEKCONDO_COOKIE_SECURE=true`, HSTS habilitado no `nginx.conf`,
`TEKCONDO_SWAGGER_ENABLED=false` (se não for necessário), segredos em cofre/variáveis de ambiente,
e rotina de backup (ver [docs/SEGURANCA.md](docs/SEGURANCA.md)).
