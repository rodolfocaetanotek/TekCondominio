# Auditoria técnica do Tek Condomínio

_Data: 28/10/2026. Base auditada: o código como estava antes das mudanças desta etapa (cópia de segurança
`tekcondo-backup-antes.tgz` fora do repositório)._ O projeto não é um repositório git. Por isso, as mudanças
desta etapa estão listadas no [RELATORIO-FINAL.md](RELATORIO-FINAL.md).

## 1. Método

- Leitura de toda a estrutura: 6 migrações Flyway, cerca de 180 classes Java e as features Angular.
- Leitura aprofundada dos módulos financeiro, cobrança, portal, segurança e testes.
- Execução das suítes de teste como linha de base, antes de qualquer alteração:
  - backend (`mvn test`): **116 testes, 0 falhas**;
  - frontend (`ng test`): **15 testes, 0 falhas**.
- Uma funcionalidade só foi considerada **Completa** com fluxo ponta a ponta: tela, API, regra no backend,
  persistência e teste de integração contra PostgreSQL 16 real (embarcado).

## 2. Arquitetura encontrada

| Item | Situação |
|---|---|
| Stack | Java 21, Spring Boot 3.5.16, Spring Security + JWT (Nimbus), JPA/Hibernate 6, Flyway, PostgreSQL 16, springdoc 2.8. Angular 22 com Material M3, signals e formulários reativos. Está alinhada ao exigido e **foi mantida**. |
| Organização | Monólito modular por pacote (`identity`, `condominium`, `structure`, `people`, `finance`, `billing`, `occurrence`, `assembly`…). As camadas controller → service → repository são respeitadas e usam DTOs (records). |
| Multiempresa | `organization_id` vem só do token. Há FKs compostas `(id, organization_id)` / `(id, condominium_id)`, que impedem no próprio banco o vínculo entre clientes diferentes. `CondominiumAccess` é a porta única de autorização: responde 404 para dado de outro cliente e 403 sem permissão. Coberto por `TenantIsolationIT`. **Ponto forte.** |
| Autorização | Permissões por módulo e ação, agrupadas em papéis com escopo de organização ou de condomínio. São lidas do banco a cada requisição, então a revogação vale na hora. Toda checagem é feita no backend. |
| Financeiro | Valores em `numeric(15,2)` e `BigDecimal`, bloqueio otimista, nada é apagado fisicamente (estorno e cancelamento com justificativa). Multa e juros ficam "fotografados" no título. O rateio fecha o centavo. Há índices únicos contra duplicidade (nota do fornecedor, rateio da competência, cobrança por unidade). **Ponto forte.** |
| Auditoria | `audit_log` só aceita inclusão, é gravado na mesma transação da operação e nunca registra segredos. |
| Erros | RFC 9457 (`problem+json`) com `code` estável e mensagens em pt-BR. |
| Testes | Integração contra PostgreSQL real embarcado (zonky), mais testes unitários das regras de cálculo. |
| Infra | Docker Compose (db, api, nginx), `.env.example` sem segredos, scripts de backup e restauração. Não foram executados nesta máquina, que não tem Docker. |

## 3. Funcionalidades: situação real (antes desta etapa)

| Módulo | Situação | Observação |
|---|---|---|
| Administradoras, usuários, papéis, convite e recuperação de senha | Completo | `AuthIT`, `PasswordIT`, `UserManagementIT`, `PermissionIT` |
| Condomínios, blocos, unidades, fração ideal, importação CSV | Completo | `CondominioIT`, `EstruturaPessoaIT` |
| Pessoas e ocupações (proprietário, inquilino, morador, responsável financeiro) | Completo | histórico de vínculos |
| Plano de contas, centros de custo, contas bancárias | Completo | `FinanceIT` |
| Contas a pagar e a receber, aprovação, baixa parcial, estorno | Completo | `FinanceIT` |
| Rateio (fração, igualitário, fixo, consumo) e cobranças | Completo | `CobrancaIT`, `RateioCalculatorTest` |
| Inadimplência, demonstrativo de débitos, acordos | Completo | `CobrancaIT` |
| Conciliação bancária | **Parcial** | só marcação manual movimento a movimento; não havia importação de extrato |
| Boleto, PIX e retorno bancário | **Ausente** | as cobranças eram baixadas à mão |
| Fechamento mensal / bloqueio de período | **Ausente** | era possível lançar, estornar ou conciliar em meses já prestados |
| Previsão orçamentária (previsto × realizado) | Completo | |
| Projeção de fluxo de caixa | **Ausente** | o relatório de fluxo era só histórico |
| Fornecedores e contratos | Completo | |
| Ocorrências, comunicados, documentos | Completo | `OperationsIT` |
| Assembleias | **Parcial** | os votos eram registrados pela mesa; não havia votação online pelo morador |
| Reservas de áreas comuns | **Ausente** | a conta "1.06 Locação de áreas comuns" existia, sem o módulo |
| Portaria (visitantes, prestadores, entregas) | **Ausente** | |
| Portal do morador | Completo | só dados da própria unidade (`ReportsDashboardPortalIT`) |
| Relatórios (13) com PDF/Excel/CSV e painel | Completo | |
| Limite de requisições por IP | **Ausente** | havia só o bloqueio por conta |
| LGPD: retenção executada por rotina | **Ausente** | |
| Correção monetária por índice | Ausente | premissa P03 |
| IA | Ausente | |

## 4. Riscos e fragilidades encontrados

| # | Risco | Gravidade | Situação |
|---|---|---|---|
| R1 | **Sem fechamento de período**: estornos e lançamentos retroativos podem alterar uma prestação de contas já entregue ao conselho. | Alta | **Corrigido** (V7, `PeriodLock`) |
| R2 | **Baixa manual de todas as cobranças**: em volume, gera erro operacional e atraso, além de não haver conciliação com o extrato. | Alta | **Mitigado**: retorno CNAB 240 (V9) e OFX (V8). Falta registro do boleto e PIX via API (depende de banco). |
| R3 | **Datas de negócio calculadas em UTC** (`Clock.systemUTC()` + `LocalDate.now(clock)`). Entre 21h e 0h no horário de Brasília, "hoje" já é o dia seguinte: afeta o cálculo de atraso, a "data futura" e o painel. | Média | **Corrigido**: o `Clock` da aplicação usa `America/Sao_Paulo` (`TEKCONDO_TIME_ZONE`) e os testes rodam no mesmo fuso (`ClockConfigTest`). |
| R4 | `hibernate.jdbc.time_zone=UTC` desloca valores `LocalTime`. Foi detectado ao gravar horários de reserva. | Média | Contornado nos módulos novos (minutos do dia + `MinuteOfDay`). Nenhum módulo antigo usa `LocalTime`. |
| R5 | Força bruta distribuída entre contas (sem limite por IP). | Média | **Corrigido**: `AuthRateLimitFilter` (em memória, por instância). |
| R6 | Isolamento depende só da aplicação + FKs, sem Row-Level Security. | Baixa | Continua pendente (testado por `TenantIsolationIT`). |
| R7 | Sem varredura de dependências no CI (OWASP / `npm audit`). Não há pipeline de CI. | Média | **Corrigido**: `.github/workflows/ci.yml`. Vulnerabilidade alta do Vite (só desenvolvimento) corrigida. OWASP precisa do segredo `NVD_API_KEY`. |
| R8 | Docker Compose e scripts de backup nunca executados neste ambiente. | Média | Pendente: precisa de uma máquina com Docker |
| R9 | O projeto não está sob controle de versão (git). | Alta (processo) | **Corrigido**: repositório git com o estado anterior e o atual. Falta o remoto. |
| R10 | Instância local de desenvolvimento antiga (iniciada em 27/10) ainda rodando na porta 8080 com o código anterior. | Informativo | Não foi encerrada. A validação desta etapa usou uma instância isolada na porta 8090. |

## 5. Pontos fortes a preservar

- Isolamento multiempresa em duas camadas (aplicação + FKs compostas), com testes dedicados.
- Integridade financeira: precisão decimal, estorno em vez de exclusão, índices contra duplicidade e multa e juros fotografados no título.
- Autorização granular por condomínio, avaliada no backend a cada requisição.
- Testes de integração contra PostgreSQL real, sem mocks de banco.
- Documentação de premissas de negócio (`ARQUITETURA.md`) e checklist de segurança e LGPD.
