# Acompanhamento de progresso — Tek Condomínio

_Atualizado em 28/10/2026._ Legenda: ✅ concluído e testado · 🟡 parcial · ⬜ pendente

## Fases

| Fase | Situação |
|---|---|
| 1 — Análise, arquitetura, modelo de dados, plano | ✅ [ARQUITETURA.md](ARQUITETURA.md) |
| 2 — Estrutura backend/frontend, Flyway, perfis, OpenAPI, Docker Compose | ✅ (Compose não executado nesta máquina — sem Docker) |
| 3 — Fundação: autenticação, usuários, organizações, permissões, condomínios, unidades, pessoas, auditoria, isolamento | ✅ |
| 4 — Financeiro: plano de contas → centros de custo → contas → lançamentos → pagar/receber → rateios → cobranças → pagamentos → inadimplência → relatórios | ✅ |
| 5 — Assembleias, atas, ocorrências, contratos, fornecedores, documentos, comunicação/notificações | ✅ |
| 6 — Portal do morador | ✅ |
| 7 — Qualidade e implantação (testes, segurança, backup/restauração, documentação) | 🟡 ver pendências |

## Funcionalidades por módulo

| Módulo | Backend | Frontend | Testes de integração |
|---|---|---|---|
| Autenticação (JWT + refresh rotativo, bloqueio, convite, esqueci/troca de senha) | ✅ | ✅ | `AuthIT`, `PasswordIT` |
| Administradoras (plataforma) + convite de administradores + ativar/inativar | ✅ | ✅ | `OrganizationIT` |
| Usuários e papéis por organização/condomínio | ✅ | ✅ | `UserManagementIT`, `PermissionIT` |
| Condomínios + parâmetros financeiros | ✅ | ✅ | `CondominiumIT`, `FinanceIT` |
| Blocos, unidades, fração ideal, importação CSV | ✅ | ✅ | `StructurePeopleIT` |
| Pessoas (CPF/CNPJ único), ocupações com histórico, responsável financeiro | ✅ | ✅ | `StructurePeopleIT` |
| Plano de contas (padrão + próprio), centros de custo, contas bancárias com saldo | ✅ | ✅ | `FinanceIT` |
| Contas a pagar/receber, recorrência, aprovação, baixas parciais, estorno, cancelamento, anexos | ✅ | ✅ | `FinanceIT` |
| Transferências, extrato, conciliação bancária | ✅ | ✅ | `FinanceIT` |
| Orçamento anual (previsto × realizado) | ✅ | ✅ | `FinanceIT` |
| Rateio (fração ideal, igualitário, fixo, consumo) e geração de cobranças | ✅ | ✅ | `BillingIT`, `AllocationCalculatorTest` |
| Multa e juros configuráveis (fotografados na emissão) | ✅ | ✅ | `LateFeeCalculatorTest`, `FinanceIT` |
| Inadimplência, demonstrativo de débitos, histórico de contatos, acordos | ✅ | ✅ | `BillingIT` |
| Fornecedores e contratos (vigência, reajuste, alertas, histórico) | ✅ | ✅ | `OperationsIT` |
| Ocorrências (SLA, interações internas/públicas, indicadores) | ✅ | ✅ | `OperationsIT` |
| Comunicados (público-alvo, e-mail, leitura) e notificações internas | ✅ | ✅ | `OperationsIT` |
| Assembleias (convocação, presença, procuração, votação ponderada, deliberações, ata) | ✅ | ✅ | `OperationsIT` |
| Documentos (validação de conteúdo, acesso autenticado, visibilidade para moradores) | ✅ | ✅ | `OperationsIT` |
| Relatórios (13) com exportação PDF/Excel/CSV auditada | ✅ | ✅ | `ReportsDashboardPortalIT` |
| Painel gerencial (filtro por condomínio e período, série de 12 meses) | ✅ | ✅ | `ReportsDashboardPortalIT` |
| Portal do morador (unidades, cobranças, comunicados, documentos, ocorrências, assembleias, agenda) | ✅ | ✅ | `ReportsDashboardPortalIT` |
| Lembretes diários (contratos, aprovações, cobranças a vencer) | ✅ | (sino) | `OperationsIT` |
| Auditoria | ✅ | ✅ | vários |
| **Fechamento mensal** (bloqueio de período, conferência, fotografia de saldos, reabertura justificada) | ✅ | ✅ | `PeriodClosingIT` |
| **Extrato OFX** (importação idempotente, conciliação automática inequívoca, casar/desfazer/ignorar/lançar tarifa) | ✅ | ✅ | `StatementImportIT`, `OfxParserTest`, `StatementMatcherTest` |
| **Retorno de cobrança CNAB 240** (baixa automática, multa/juros, pendências, sem duplicidade) | ✅ | ✅ | `BankReturnIT`, `Cnab240ReturnParserTest` |
| **Reservas de áreas comuns** (regras por área, aprovação, taxa → cobrança, inadimplente, portal) | ✅ | ✅ | `ReservationIT` |
| **Portaria** (pré-autorização com código, entrada/saída, aviso ao morador, anonimização LGPD) | ✅ | ✅ | `VisitorIT` |
| **Indicadores** (projeção de caixa por vencimentos, despesas fora do padrão) | ✅ | ✅ | `InsightIT`, `InsightCalculatorTest` |
| **Limite por IP na autenticação** | ✅ | — | `AuthRateLimitFilterTest` |

## Verificação final

- `mvn test`: **141 testes, 0 falhas** (unitários + integração contra PostgreSQL 16 real).
- `ng test`: **20 testes, 0 falhas**. `ng build` de produção sem erros.
- `scripts/e2e_validation.py`: **19 verificações OK** contra a API em execução (instância isolada, 28/10/2026).
- Percurso ponta a ponta pelo proxy do frontend contra o banco local (estrutura → financeiro → cobrança → operações → relatórios/PDF → painel).
- **Não verificado nesta máquina:** aparência no navegador (sem ferramenta de navegador na sessão), envio SMTP real, `docker compose up` e os scripts de backup (dependem de Docker).

## Pendências conhecidas (não implementadas)

| Item | Observação |
|---|---|
| Registro de boletos/PIX via API bancária e remessa CNAB | Exige banco e credenciais. A **baixa automática pelo retorno CNAB 240 já existe**; falta o registro (ver INTEGRACOES.md). CNAB 400 não implementado. |
| Votação eletrônica pelos moradores | Votos são registrados pela mesa (presencial/híbrida). Depende também de análise jurídica da convenção. |
| Assistente de IA | Avaliado e não implementado (MATRIZ-COMPARATIVA.md §4). |
| Direitos do titular (LGPD): exportação/anonimização automática e política de retenção executada por job | Processo documentado; execução manual. |
| Row-Level Security no PostgreSQL e limite por IP fora da autenticação | Isolamento hoje é garantido pela aplicação + FKs compostas (testado). |
| Correção monetária por índice (IPCA/IGP-M) | Premissa P03: sem índice automático. |
| Importação de pessoas avulsas por planilha | A importação de unidades já cadastra os proprietários. |
| Armazenamento em nuvem (S3) | Porta `FileStorage` pronta; implementação local em disco. |
