# Glossário e convenções de nomenclatura (pt-BR)

_28/10/2026. Referência: convenções observadas no código público do TekPonto (rotas, chamadas de API, campos e
seletores), mais as decisões tomadas para o Tek Condomínio._

## Convenções

| Item | Regra | Exemplo |
|---|---|---|
| Nome de negócio | Português sem acento nem cedilha nos identificadores | `Condominio`, `Lancamento`, `Cobranca` |
| Sufixo técnico | Em inglês, como no TekPonto (`admissaoService`, `loginForm`) | `CondominioService`, `LancamentoController`, `UnidadeRepository`, `CondominioRequest`, `RateioCalculator` |
| Identificadores de chave | Prefixo `id` + entidade | `idCondominio`, `idUnidade`, `idAdministradora` |
| Datas e instantes | `data` + qualificador; instantes `...Em` | `dataVencimento`, `dataInicio`, `dataFim`, `criadoEm`, `atualizadoEm` |
| Booleanos | Adjetivo ou particípio | `ativo`, `pago`, `exigeAprovacao` |
| Pacotes Java | Módulo em português; subpacotes técnicos em inglês | `br.com.tekcondo.financeiro.service`, `...financeiro.entity` |
| Arquivos Angular | kebab-case em português | `condominio-lista.ts`, `lancamento-detalhe.ts` |
| Seletores Angular | Prefixo `tc-` + português | `tc-lancamento-detalhe` |
| Rotas de tela | kebab-case em português (já era o padrão) | `/financeiro/lancamentos/:id` |
| API REST | Caminhos e campos JSON em português, verbos HTTP mantidos | `GET /api/v1/condominios/{id}/financeiro/lancamentos` |
| Parâmetros de paginação | `pagina`, `tamanho`, `ordem` | `?pagina=0&tamanho=20&ordem=nome,asc` |

## O que continua em inglês (e por quê)

- **Banco de dados.** Tabelas, colunas, índices e restrições ficam como estão. As entidades declaram explicitamente
  `@Table`/`@Column`/`@JoinColumn` com o nome atual, então trocar o nome de um campo Java não altera o schema
  (decisão: manter e mapear).
- **Códigos gravados.** As constantes dos enums continuam em inglês: situações (`OPEN`, `PAID`…), permissões
  (`FINANCE_VIEW`…), ações de auditoria e papéis. Elas são gravadas no banco e no histórico de auditoria. A
  interface já mostra rótulos em português. O nome do *tipo* enum é traduzido (`SituacaoLancamento`).
- **Frameworks e bibliotecas.** Anotações, métodos obrigatórios de interfaces (`toString`, `equals`, `configure`,
  `ngOnInit`…), nomes de propriedades do Spring (`spring.*`, `server.*`) e APIs de terceiros.
- **Termos técnicos consagrados.** `token`, `refresh token`, `JWT`, `login`, `email`, `hash`, `CNAB`, `OFX`, `PIX`,
  `CSV`, `PDF`, `URL`, `IP`, `API`, `DTO`.
- **Contratos de configuração e de protocolo.** Chaves `tekcondo.*` (`TekCondoProperties`, `application*.yml`,
  variáveis `TEKCONDO_*`), nomes das *claims* do JWT (`org`, `name`, `email`, `pa`), cookie `tekcondo_refresh`,
  cabeçalho `X-TekCondo-Client`, autoridade `ROLE_PLATFORM_ADMIN`, caminho `/api/v1/auth` e os campos
  `accessToken`/`tokenType` da resposta de login. Os membros padrão do problem+json (RFC 9457: `type`, `title`,
  `status`, `detail`, `instance`) também.
- **Dados gravados de auditoria e logs.** Valores de `audit_log.entity_type` (`User`, `Organization`…), chaves de
  `details` (`reason`, `name`, `from`, `to`, `role`…) e chaves de MDC dos logs (`requestId`, `userId`,
  `organizationId`) não mudam: são dados históricos e campos de observabilidade.
- **Códigos de erro da API.** `NOT_FOUND`, `VALIDATION_ERROR`, `INVALID_SORT`… são códigos estáveis para o cliente.
- **Formatos externos.** Campos dos layouts OFX e CNAB 240 (`FITID`, `TRNAMT`, segmentos T/U) seguem o padrão
  do banco ou do layout. Por isso a linha do extrato expõe `fitId` (FITID do OFX) com esse nome.
- **Códigos de movimentação e fotografia do fechamento.** Os tipos de movimentação do extrato/conciliação
  (`PAYMENT`, `TRANSFER_IN`, `TRANSFER_OUT`, `TRANSFER`) são códigos estáveis trocados com o cliente. As chaves do
  JSON gravado em `period_closing.snapshot` (`from`, `until`, `income`, `expense`, `unreconciledMovements`,
  `pendingApprovals`, `balances[].account/balance/reconciled`) são dado histórico e continuam em inglês; o campo da
  API que as carrega chama-se `fotografia`.
- **Planilha de importação de unidades.** O cabeçalho CSV documentado (`bloco; unidade; tipo; fracao; area;
  proprietario; documento; email; telefone`) e os apelidos da coluna `tipo` já são em português e continuam aceitos
  como estão (não quebram as planilhas dos usuários).
- **Caminhos de outros módulos sob `/condominios/{id}`.** O recurso raiz passou a `/api/v1/condominios`; os
  subcaminhos dos módulos ainda não padronizados (`/billing`, `/announcements`...) mudam no lote de cada módulo.

## Módulos (pacotes Java / pastas)

| Atual | Português |
|---|---|
| `announcement` | `comunicado` |
| `assembly` | `assembleia` |
| `audit` | `auditoria` |
| `billing` | `cobranca` |
| `condominium` | `condominio` |
| `dashboard` | `painel` |
| `document` | `documento` |
| `finance` | `financeiro` |
| `identity` | `acesso` |
| `insight` | `indicador` |
| `integration` | `integracao` |
| `jobs` | `rotina` |
| `notification` | `notificacao` |
| `occurrence` | `ocorrencia` |
| `organization` | `administradora` |
| `people` | `pessoa` |
| `portal` | `portal` |
| `report` | `relatorio` |
| `reservation` | `reserva` |
| `shared` | `comum` |
| `structure` | `estrutura` |
| `supplier` | `fornecedor` |
| `visitor` | `portaria` |

## Entidades e conceitos de negócio

| Inglês | Português | | Inglês | Português |
|---|---|---|---|---|
| Organization | Administradora | | Condominium | Condominio |
| CondominiumType / Status | TipoCondominio / SituacaoCondominio | | Address | Endereco |
| Building | Bloco | | Unit | Unidade |
| Person / People | Pessoa | | Occupancy | Vinculo |
| Owner | Proprietario | | Tenant | Inquilino |
| Resident | Morador | | Financial responsible (payer) | ResponsavelFinanceiro |
| User | Usuario | | Role / UserRole | Papel / PapelUsuario |
| RoleScope / AccessScope | EscopoPapel / EscopoAcesso | | Permission / PermissionGrant | Permissao / ConcessaoPermissao |
| PasswordToken | TokenSenha | | RefreshToken | RefreshToken (técnico) |
| CurrentUser | UsuarioAtual | | AuditLog / AuditAction | RegistroAuditoria / AcaoAuditoria |
| FinancialEntry (entry) | Lancamento | | EntryPayment (payment) | Baixa |
| Kind INCOME/EXPENSE | Natureza (receita/despesa) | | Approval | Aprovacao |
| Origin | Origem | | ChartAccount | ContaPlano (conta do plano de contas) |
| CostCenter | CentroCusto | | BankAccount | ContaBancaria |
| BankTransfer (transfer) | Transferencia | | Treasury | Tesouraria |
| Budget / BudgetLine | Orcamento / LinhaOrcamento | | Reconcile / reconciliation | Conciliar / Conciliacao |
| StatementImport / StatementLine | ImportacaoExtrato / LinhaExtrato | | PeriodClosing / PeriodLock | FechamentoPeriodo / BloqueioPeriodo |
| LateFee (fine / interest) | Encargos (multa / juros) | | Billing | Cobranca |
| Allocation | Rateio | | Charge | Cobranca (título de cobrança) |
| Competence | Competencia | | Agreement | Acordo |
| Installment | Parcela | | Delinquency / overdue | Inadimplencia / vencido |
| CollectionNote | NotaCobranca | | CollectionNumber | NumeroCobranca |
| BankReturnFile / BankReturnItem | ArquivoRetorno / ItemRetorno | | Supplier | Fornecedor |
| Contract / ContractHistory | Contrato / HistoricoContrato | | Occurrence / OccurrenceInteraction | Ocorrencia / InteracaoOcorrencia |
| Announcement | Comunicado | | Audience | PublicoAlvo |
| Assembly / AgendaItem | Assembleia / ItemPauta | | Attendance / Vote | Presenca / Voto |
| Document | Documento | | Attachment | Anexo |
| CommonArea | AreaComum | | Reservation | Reserva |
| VisitorPass | AutorizacaoVisita | | Visitor / ServiceProvider / Delivery | Visitante / PrestadorServico / Entrega |
| Notification | Notificacao | | Report | Relatorio |
| Dashboard | Painel | | Insight | Indicador |
| Job | Rotina (classe `...Job`) | | Mail / MailGateway | Email / EmailGateway |
| FileStorage | ArmazenamentoArquivos | | Page / PageResponse | Pagina / PaginaResponse |
| PlatformAdmin | AdministradorPlataforma | | Assignment (role assignment) | Atribuicao (de papel) |
| Invite | Convite | | Profile / Me | Perfil / PerfilResponse |
| Access (authorization) | Acesso (`AcessoService`) | | UserGrants / grant | ConcessoesUsuario / concessao |
| Password / PasswordService | Senha / SenhaService | | PasswordToken.Purpose | TokenSenha.Finalidade |
| AuthenticationFailed | FalhaAutenticacao | | AccountEmails | EmailsConta |
| ClientIp | IpCliente | | Inbox | CaixaEntrada |
| MailMessage | MensagemEmail | | Audit query | ConsultaAuditoria |
| BrazilianDocuments / BrazilianNumbers | DocumentosBrasileiros / NumerosBrasileiros | | BrazilianState / Phone / ZipCode | Uf / Telefone / Cep |
| StrongPassword | SenhaForte | | FieldError (problem+json) | ErroCampo |
| OFX Transaction / Statement | Transacao / Extrato | | CNAB return file (parser) | Retorno (`RetornoCnab240Parser`) |
| CSV Row | Linha | | Notification read / unread | lida / naoLidas |

## Campos frequentes

| Inglês | Português | | Inglês | Português |
|---|---|---|---|---|
| name / description / notes | nome / descricao / observacoes | | title / body | titulo / texto |
| status / kind / type | situacao / natureza / tipo | | code / number | codigo / numero |
| amount / total / balance | valor / total / saldo | | paidAmount / openAmount | valorPago / valorAberto |
| discount / fine / interest | desconto / multa / juros | | dueDate / paidAt | dataVencimento / pagoEm |
| date / from / to | data / de / ate | | start / end | inicio / fim |
| createdAt / updatedAt | criadoEm / atualizadoEm | | createdBy / updatedBy | criadoPor / atualizadoPor |
| active / enabled | ativo / habilitado | | reason | motivo |
| phone / document (CPF/CNPJ) | telefone / documento | | fraction (ideal) | fracaoIdeal |
| page / size / sort / content | pagina / tamanho / ordem / conteudo | | totalElements / totalPages | totalElementos / totalPaginas |
| items / lines | itens / linhas | | version | versao |
| password / newPassword / currentPassword | senha / novaSenha / senhaAtual | | search | busca |
| lastLoginAt / lockedUntil | ultimoLoginEm / bloqueadoAte | | expiresAt / usedAt / revokedAt | expiraEm / usadoEm / revogadoEm |
| pendingInvite | convitePendente | | roleCode / roleName | codigoPapel / nomePapel |
| occurredAt / action / details | ocorridoEm / acao / detalhes | | entityType / entityId | tipoEntidade / idEntidade |
| userEmail / ipAddress | emailUsuario / enderecoIp | | dedupKey | chaveDeduplicacao |
| problem+json `code` / `errors` | `codigo` / `erros` (`campo`, `mensagem`) | | from / to (filtros) | de / ate |
| legalName | razaoSocial | | address (zipCode, street, number, complement, district, city, state) | endereco (cep, logradouro, numero, complemento, bairro, cidade, uf) |
| inactivatedAt / inactivationReason | inativadoEm / motivoInativacao | | finePercent / interestMonthlyPercent | percentualMulta / percentualJurosMensal |
| billingDueDay / requirePayableApproval | diaVencimento / exigeAprovacaoContasPagar | | identifier / label (unidade) | identificacao / rotulo |
| floor | andar | | ownerName / financialResponsibleName | nomeProprietario / nomeResponsavelFinanceiro |
| activeUnits | unidadesAtivas | | balanced / difference (frações) | fechada / diferenca |
| dryRun | simulacao | | buildingsCreated / unitsCreated / peopleCreated / peopleReused | blocosCriados / unidadesCriadas / pessoasCriadas / pessoasReaproveitadas |
| secondaryPhone | telefoneSecundario | | portalUser / portal-access | usuarioPortal / acesso-portal |
| Person.Kind (física/jurídica) | Pessoa.Tipo (`tipo`) | | Occupancy.Role (OWNER/TENANT/RESIDENT) | Vinculo.Tipo (`tipo`; evita conflito com `Papel` de acesso) |
| financialResponsible | responsavelFinanceiro | | startDate / endDate / endReason (vínculo) | dataInicio / dataFim / motivoEncerramento |
| current (vínculo) | vigente | | file (multipart da importação de unidades) | arquivo |
| CondominiumOption / accessible | OpcaoCondominio / acessiveis | | CondominiumAccess (require / requireActive) | AcessoCondominio (exigir / exigirAtivo) |
| FractionSummary / ImportResult / ImportError | ResumoFracoes / ResultadoImportacao / ErroImportacao | | Summary / Detail (DTOs) | Resumo / Detalhe |

## Financeiro e indicadores (lote 3)

| Inglês | Português | | Inglês | Português |
|---|---|---|---|---|
| FinancialEntry.Kind / Status / Approval / Origin | Lancamento.Natureza / Situacao / Aprovacao / Origem | | EntryPayment.Method | Baixa.FormaPagamento (`formaPagamento`) |
| ChartAccount.Nature / analytic / system | ContaPlano.Natureza / `analitica` / `sistema` | | BankAccount.Kind | ContaBancaria.Tipo (`tipo`) |
| chartAccountId / costCenterId / supplierId / unitId / personId | idContaPlano / idCentroCusto / idFornecedor / idUnidade / idPessoa | | bankAccountId / paymentId / transferId | idContaBancaria / idBaixa / idTransferencia |
| remaining | valorAberto | | overdue | vencido |
| competence | competencia | | documentNumber / collectionNumber | numeroDocumento / numeroCobranca |
| installment / installments / installmentLabel | parcela / totalParcelas / rotuloParcela | | repeat (lançamento) | repeticoes |
| recurrenceGroup | grupoRecorrencia | | allocationId / agreementId | idRateio / idAcordo |
| approvalNote / approvedAt / approvedBy | observacaoAprovacao / aprovadoEm / aprovadoPor | | cancelReason / cancelledAt | motivoCancelamento / canceladoEm |
| note (decisão, linha do extrato) | observacao | | reason (justificativa) | motivo (`MotivoRequest`) |
| paidOn / principal | dataPagamento / principal | | reconciled / reversed / reversedAt / reversalReason | conciliada / estornada / estornadaEm / motivoEstorno |
| Quote (encargos) / daysLate | CalculoEncargos / diasAtraso | | pay / reverse / approve / reject / cancel | baixar / estornar / aprovar / reprovar / cancelar |
| openingBalance / openingDate / closingBalance / reconciledBalance | saldoInicial / dataAbertura / saldoFinal / saldoConciliado | | bankCode / agency / accountNumber | codigoBanco / agencia / numeroConta |
| fromAccount / toAccount (transferência) | contaOrigem / contaDestino | | Statement / StatementLine (movimento) | Extrato / Movimentacao (`movimentacoes`) |
| ReconcileRequest / ReconcileItem / items | ConciliacaoRequest / ItemConciliacao / itens | | Settings (financeiro) | ParametrosFinanceiros (`/parametros`) |
| BudgetCell / BudgetRow / cells / rows | CelulaOrcamento / ContaOrcamento / celulas / contas | | budgeted / realized / year / month | previsto / realizado / ano / mes |
| PeriodClosing.Action / closedUntil / previousUntil | FechamentoPeriodo.Acao / fechadoAte / fechadoAteAnterior | | suggestedUntil / history / snapshot | sugestaoAte / historico / fotografia |
| ClosingCheck / until / unreconciledMovements / pendingApprovals | Verificacao / ate / movimentacoesNaoConciliadas / aprovacoesPendentes | | income / expense (totais) | receitas / despesas |
| createdByName | nomeCriadoPor | | PeriodLock.ensureOpen | BloqueioPeriodo.exigirAberto |
| StatementImport (fileName, fileHash, periodStart/End, linesTotal/New, autoMatched) | ImportacaoExtrato (nomeArquivo, hashArquivo, inicioPeriodo/fimPeriodo, totalLinhas/linhasNovas, conciliadasAutomaticamente) | | StatementLine (postedOn, trnType, memo, status) | LinhaExtrato (dataLancamento, tipoTransacao, historico, situacao) |
| match / unmatch / ignore / register (linha) | conciliar / desfazer-conciliacao / ignorar / lancar | | suggestions / matchedType / matchedId | sugestoes / tipoConciliado / idConciliado |
| StatementMatcher (Line, Candidate, autoMatch) | ConciliacaoExtratoMatcher (Linha, Candidato, paresInequivocos) | | FinanceQueries / FinanceSetupService | ConsultasFinanceiras / ConfiguracaoFinanceiraService |
| CashProjection (asOf, currentBalance, overdueReceivables/Payables, lowest…) | ProjecaoCaixa (dataBase, saldoAtual, aReceberVencido/aPagarVencido, menorSaldoProjetado, dataMenorSaldo) | | Week (expectedIn/Out, projectedBalance) | Semana (entradasPrevistas/saidasPrevistas, saldoProjetado) |
| ExpenseAnomaly (historicalMean, ratio, explanation) | DespesaForaDoPadrao (mediaHistorica, razao, explicacao) | | Due / Deviation (mean, stdDev) | Vencimento / Desvio (media, desvioPadrao) |
