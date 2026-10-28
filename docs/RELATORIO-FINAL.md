# Relatório final — etapa de benchmarking e evolução (28/10/2026)

## 1. Resumo

O sistema encontrado já era sólido: 116 testes, isolamento multiempresa comprovado e financeiro íntegro. A
auditoria ([AUDITORIA.md](AUDITORIA.md)) e a pesquisa de mercado ([BENCHMARK.md](BENCHMARK.md),
[MATRIZ-COMPARATIVA.md](MATRIZ-COMPARATIVA.md)) mostraram quatro lacunas que todos os concorrentes cobrem (S em
todos no benchmark):

- reservas de áreas comuns;
- portaria (visitantes, prestadores e entregas);
- conciliação por extrato;
- baixa automática de boletos.

Mostraram também um risco de integridade: não havia fechamento de período. Esses pontos foram implementados
ponta a ponta, com migrações, regras no backend, telas, testes e validação integrada.

## 2. Implementado

| Módulo | Backend | Frontend | Migração |
|---|---|---|---|
| Fechamento mensal | `finance/service/PeriodLock`, `PeriodClosingService`, `PeriodClosingController`, `entity/PeriodClosing`; bloqueio aplicado em `EntryService`, `TreasuryService`, `AllocationService`, `CollectionService`, `ReservationService`, `BankReturnService` | `finance/closing-tab.ts` | `V7__period_closing.sql` |
| Extrato OFX + conciliação | `integration/bank/OfxParser`, `finance/service/StatementMatcher`, `StatementImportService`, `StatementImportController`, entidades `StatementImport`/`StatementLine`; `TreasuryService` libera a linha ao desfazer a conciliação | `finance/reconciliation-tab.ts` | `V8__bank_statement_import.sql` |
| Retorno CNAB 240 | `integration/bank/Cnab240ReturnParser`, `billing/service/BankReturnService`, `BankReturnController`, entidades `BankReturnFile`/`BankReturnItem`; `collectionNumber` em `FinancialEntry` | `billing/bank-return-tab.ts` | `V9__bank_collection_return.sql` |
| Reservas | pacote `reservation/*` (+ `MinuteOfDay`); endpoints do portal em `PortalService`/`PortalController` | `reservations/reservation-page.ts`, `portal-reservations.ts`, rota `/reservas` | `V10__reservations.sql` |
| Portaria | pacote `visitor/*`, `jobs/PrivacyRetentionJob` (anonimização LGPD); endpoints do portal | `visitors/gate-page.ts`, `portal-visitors.ts`, rota `/portaria` | `V11__visitors.sql` |
| Indicadores | pacote `insight/*` (projeção de caixa, despesas fora do padrão) | `finance/insights-tab.ts` | — |
| Limite por IP na autenticação | `shared/security/AuthRateLimitFilter` | — | — |
| Apoio | `LocalDevApplication` aceita diretório e portas alternativos; `form-dialog` aceita o campo `time`; novas permissões, ações de auditoria e rótulos | — | — |

Novas permissões:

| Permissão | Papéis |
|---|---|
| `FINANCE_CLOSE` | somente administrador |
| `RESERVATION_VIEW` / `RESERVATION_EDIT` | administrador, funcionário, síndico, subsíndico (edição); conselho e funcionário do condomínio (consulta) |
| `ACCESS_VIEW` / `ACCESS_EDIT` | administrador, funcionário, síndico e funcionário do condomínio (edição); subsíndico e conselho (consulta) |

Todas foram semeadas nas migrações, e o catálogo continua em sincronia com o banco (`PermissionCatalogIT`).

## 3. Testes executados

| Suíte | Antes | Agora | Resultado |
|---|---|---|---|
| Backend `mvn test` (unitários + integração com PostgreSQL 16 real) | 116 | **140** | 0 falhas |
| Frontend `ng test` (Vitest) | 15 | **20** | 0 falhas |
| `ng build` de produção | ok | ok | sem erros |
| Validação ponta a ponta `scripts/e2e_validation.py` (API real em execução) | — | **19 verificações** | todas OK |

Novos testes:
- `PeriodClosingIT`, `StatementImportIT`, `BankReturnIT`, `ReservationIT`, `VisitorIT`, `InsightIT`;
- `OfxParserTest`, `StatementMatcherTest`, `Cnab240ReturnParserTest`, `InsightCalculatorTest`, `AuthRateLimitFilterTest`;
- `new-modules-api.spec.ts` (contrato HTTP do frontend).

**Validado de ponta a ponta** (fase 5), via HTTP contra a aplicação rodando:
- cadastro de administradora, condomínio, unidades e responsáveis;
- rateio por fração fechando o centavo e bloqueio de geração duplicada;
- retorno CNAB baixando a cobrança com multa e juros;
- OFX conciliando o crédito, com a tarifa lançada pela linha do extrato;
- inadimplência e acordo de parcelamento;
- fechamento do mês e bloqueio de lançamento retroativo;
- reserva pelo portal com aprovação e taxa lançada como cobrança;
- visitante liberado pelo código;
- morador vendo só a própria unidade e recebendo 403 na área administrativa;
- outra administradora recebendo 404 nos dados do condomínio.

**Não verificado neste ambiente:**
- aparência no navegador (não havia ferramenta de navegador; só build e testes);
- `docker compose up` (não há Docker);
- envio SMTP real;
- arquivos OFX e CNAB **reais** de bancos (os testes usam arquivos gerados no layout padrão).

## 4. Limitações e pendências

- **Integração bancária não homologada.** O CNAB 240 segue o padrão FEBRABAN e precisa ser homologado banco a
  banco. Registro de boleto e PIX via API, remessa e CNAB 400 não foram implementados. Os detalhes estão em
  [INTEGRACOES.md](INTEGRACOES.md).
- **Datas em UTC nos módulos antigos (R3):** entre 21h e 0h, "hoje" vira o dia seguinte. Os módulos novos já
  usam America/Sao_Paulo.
- Votação online pelo morador: não implementada. Depende também de avaliação jurídica.
- Portaria sem integração com hardware (facial, placas, catracas).
- Assistente de IA: avaliado e não implementado (motivos em MATRIZ-COMPARATIVA.md §4).
- Projeto sem git e sem CI. Recomendo versionar antes de qualquer nova mudança.
- Uma instância antiga da API (iniciada em 27/10, porta 8080) continua rodando com o código anterior. Ela não
  foi encerrada. Ao reiniciá-la, as migrações V7–V11 serão aplicadas ao banco `.local-dev`. Todas só
  acrescentam estruturas; nenhuma apaga dados.

## 5. Próximas prioridades

1. Versionamento (git) e CI com testes, `npm audit` e OWASP Dependency-Check (B15).
2. Datas de negócio no fuso do condomínio (B9).
3. Homologar o retorno CNAB com o primeiro banco de um cliente real e implementar registro de boleto e PIX via API desse banco (B8).
4. Régua de cobrança automática (B12) e votação online (B10).
5. PWA do portal (B13) e exportação contábil (B14).
