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
| Autenticação (JWT + refresh rotativo, bloqueio, convite, esqueci/troca de senha) | ✅ | ✅ | `AuthIT`, `SenhaIT` |
| Administradoras (plataforma) + convite de administradores + ativar/inativar | ✅ | ✅ | `AdministradoraIT` |
| Usuários e papéis por organização/condomínio | ✅ | ✅ | `UsuarioIT`, `PermissionIT` |
| Condomínios + parâmetros financeiros | ✅ | ✅ | `CondominioIT`, `FinanceiroIT` |
| Blocos, unidades, fração ideal, importação CSV | ✅ | ✅ | `EstruturaPessoaIT` |
| Pessoas (CPF/CNPJ único), ocupações com histórico, responsável financeiro | ✅ | ✅ | `EstruturaPessoaIT` |
| Plano de contas (padrão + próprio), centros de custo, contas bancárias com saldo | ✅ | ✅ | `FinanceiroIT` |
| Contas a pagar/receber, recorrência, aprovação, baixas parciais, estorno, cancelamento, anexos | ✅ | ✅ | `FinanceiroIT` |
| Transferências, extrato, conciliação bancária | ✅ | ✅ | `FinanceiroIT` |
| Orçamento anual (previsto × realizado) | ✅ | ✅ | `FinanceiroIT` |
| Rateio (fração ideal, igualitário, fixo, consumo) e geração de cobranças | ✅ | ✅ | `CobrancaIT`, `RateioCalculatorTest` |
| Multa e juros configuráveis (fotografados na emissão) | ✅ | ✅ | `EncargosCalculatorTest`, `FinanceiroIT` |
| Inadimplência, demonstrativo de débitos, histórico de contatos, acordos | ✅ | ✅ | `CobrancaIT` |
| Fornecedores e contratos (vigência, reajuste, alertas, histórico) | ✅ | ✅ | `OperacoesIT` |
| Ocorrências (SLA, interações internas/públicas, indicadores) | ✅ | ✅ | `OperacoesIT` |
| Comunicados (público-alvo, e-mail, leitura) e notificações internas | ✅ | ✅ | `OperacoesIT` |
| Assembleias (convocação, presença, procuração, votação ponderada, deliberações, ata) | ✅ | ✅ | `OperacoesIT` |
| Documentos (validação de conteúdo, acesso autenticado, visibilidade para moradores) | ✅ | ✅ | `OperacoesIT` |
| Relatórios (13) com exportação PDF/Excel/CSV auditada | ✅ | ✅ | `RelatoriosPainelPortalIT` |
| Painel gerencial (filtro por condomínio e período, série de 12 meses) | ✅ | ✅ | `RelatoriosPainelPortalIT` |
| Portal do morador (unidades, cobranças, comunicados, documentos, ocorrências, assembleias, agenda) | ✅ | ✅ | `RelatoriosPainelPortalIT` |
| Lembretes diários (contratos, aprovações, cobranças a vencer) | ✅ | (sino) | `OperacoesIT` |
| Auditoria | ✅ | ✅ | vários |
| **Fechamento mensal** (bloqueio de período, conferência, fotografia de saldos, reabertura justificada) | ✅ | ✅ | `FechamentoPeriodoIT` |
| **Extrato OFX** (importação idempotente, conciliação automática inequívoca, casar/desfazer/ignorar/lançar tarifa) | ✅ | ✅ | `ImportacaoExtratoIT`, `OfxParserTest`, `ConciliacaoExtratoMatcherTest` |
| **Retorno de cobrança CNAB 240** (baixa automática, multa/juros, pendências, sem duplicidade) | ✅ | ✅ | `RetornoBancarioIT`, `Cnab240ReturnParserTest` |
| **Reservas de áreas comuns** (regras por área, aprovação, taxa → cobrança, inadimplente, portal) | ✅ | ✅ | `ReservaIT` |
| **Portaria** (pré-autorização com código, entrada/saída, aviso ao morador, anonimização LGPD) | ✅ | ✅ | `PortariaIT` |
| **Indicadores** (projeção de caixa por vencimentos, despesas fora do padrão) | ✅ | ✅ | `IndicadorIT`, `IndicadorCalculatorTest` |
| **Limite por IP na autenticação** | ✅ | — | `AuthRateLimitFilterTest` |

## Verificação final

- `mvn test`: **141 testes, 0 falhas** (unitários + integração contra PostgreSQL 16 real).
- `ng test`: **23 testes, 0 falhas**. `ng build` de produção sem erros.
- `scripts/e2e_validation.py`: **19 verificações OK** contra a API em execução (instância isolada, 28/10/2026).
- Percurso ponta a ponta pelo proxy do frontend contra o banco local (estrutura → financeiro → cobrança → operações → relatórios/PDF → painel).
- Navegador (Playwright): todas as telas administrativas e o portal abertos com a API real, sem erros de console
  nem respostas de erro da API (29/10/2026, após a padronização em português).
- **Não verificado nesta máquina:** envio SMTP real, `docker compose up` e os scripts de backup (dependem de Docker).

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

## Padronização em português (29/10/2026)

Código, rotas da API, DTOs, mensagens e frontend passaram para o português no padrão do TekPonto (nome de negócio
em português + sufixo técnico em inglês). Banco de dados sem alterações: as entidades mapeiam explicitamente as
tabelas e colunas existentes. Detalhes, mapa de nomes e itens mantidos em inglês: [GLOSSARIO.md](GLOSSARIO.md).
