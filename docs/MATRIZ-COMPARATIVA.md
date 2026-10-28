# Matriz comparativa, lacunas e backlog — Tek Condomínio

_28/10/2026._ As evidências dos concorrentes, com fontes (F1–F61) e data de consulta, estão em [BENCHMARK.md](BENCHMARK.md).
A situação do Tek foi verificada no código e em testes ([AUDITORIA.md](AUDITORIA.md)).

**Legenda.** Para o Tek: **Completo** = ponta a ponta com teste de integração; **Parcial**; **Ausente**. Para os
concorrentes: **S** = há evidência pública; **P** = evidência parcial; **NV** = não verificado. NV **não**
significa que o recurso não existe.

Siglas das colunas: Sib = Sibrax; Sup = Superlógica; uC = uCondo; Town = TownSq; Grp = Group/C21. As colunas
"Tek antes" e "Tek agora" mostram a situação do Tek antes e depois desta etapa.

## 1. Matriz por funcionalidade

| # | Funcionalidade | Sib | Sup | uC | Town | Grp | Tek antes | Tek agora | Diferença / impacto | Prior. | Complex. |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Financeiro: plano de contas, lançamentos, centros de custo | S | S | S | P | S | Completo | Completo | Paridade. Falta exportação contábil (SPED/ECD), que Sib, Grp e Sup oferecem. | P2 | M |
| 2 | Contas a pagar/receber com aprovação | S | S | S | P | S | Completo | Completo | Paridade | — | — |
| 3 | Boleto registrado + PIX via banco | P | S | S | P | S | Ausente | **Parcial** | Retorno CNAB 240 com baixa automática **implementado**. Falta registro do boleto (remessa/API) e PIX, que dependem de banco homologado. É a principal lacuna comercial restante. | P1 | A |
| 4 | Conciliação bancária (OFX/CNAB) | S | S | S | P | S | Parcial | **Completo** | Importação OFX, conciliação automática inequívoca, lançamento de tarifa a partir da linha. | — | — |
| 5 | Inadimplência, multa, juros, acordos | S | S | S | P | S | Completo | Completo | Falta régua de cobrança automática multicanal (Sup) | P2 | M |
| 6 | Orçamento e prestação de contas | S | S | S | P | S | Parcial (sem fechamento) | **Completo** | Fechamento mensal com fotografia de saldos e bloqueio retroativo | — | — |
| 7 | Rateio por fração ideal e critérios | P | NV | P | NV | P | Completo | Completo | **Diferencial**: nenhum site mostra a fração ideal explícita | — | — |
| 8 | Cadastros (unidades, pessoas, vínculos) | NV | P | S | P | P | Completo | Completo | Faltam veículos e pets (uC) | P3 | B |
| 9 | Síndicos, conselho, fornecedores | P | P | P | P | P | Completo | Completo | Papéis por condomínio | — | — |
| 10 | Assembleias e votação online | P | S | S | P | S | Parcial | Parcial | Falta votação online pelo morador. A validade jurídica depende da convenção e da Lei 14.309/2022. | P2 | A |
| 10a | Procurações | NV | NV | NV | NV | NV | Completo | Completo | **Diferencial**: nenhum concorrente com evidência | — | — |
| 11 | Reservas de áreas comuns | S | S | S | S | S | Ausente | **Completo** | Regras por área, aprovação, taxa → cobrança, bloqueio de inadimplente, portal | — | — |
| 12 | Comunicados e notificações | S | S | S | S | S | Completo | Completo | Faltam push mobile e WhatsApp | P3 | M |
| 13 | Portaria: visitantes, prestadores, entregas | S | S | S | S | S | Ausente | **Completo** (software) | Pré-autorização com código, entrada e saída, aviso ao morador, anonimização LGPD. Integração com hardware (facial, placas) é NV. | P3 | A (hardware) |
| 14 | Ocorrências e solicitações | P | S | S | S | P | Completo | Completo | Paridade | — | — |
| 15 | App ou portal do morador | S | S | S | S | S | Completo (web) | Completo (web responsivo) | Falta app nativo e PWA instalável | P2 | M |
| 16 | Painel do síndico e da administradora | P | S | S | S | S | Completo | Completo + indicadores | Projeção de caixa e despesas fora do padrão | — | — |
| 17 | Contratos e documentos | P | P | P | P | S | Completo | Completo | Falta assinatura digital (Grp) | P3 | M |
| 18 | Relatórios | S | S | S | P | S | Completo (13) | Completo | Sup declara mais de 70 relatórios | P3 | B |
| 19 | Integrações (bancos, contabilidade) | P | S | S | P | S | Ausente | Parcial | Adaptadores CNAB 240 e OFX. Faltam API bancária e exportação contábil. | P1 | A |
| 20 | Automações (lembretes, alertas) | P | S | S | P | S | Parcial | Parcial+ | Lembretes diários + retenção LGPD. Falta régua de cobrança. | P2 | M |
| 21 | IA e análise de dados | NV | P | S | P | S | Ausente | Parcial (analítico) | Indicadores determinísticos. Assistente de IA avaliado e **não implementado** (§4). | P3 | A |
| 22 | Segurança, permissões, LGPD | NV | P | P | NV | P | Completo (sem rate limit/retenção) | Completo+ | **Diferencial documentável**: isolamento testado, auditoria, limite por IP, retenção | — | — |
| 23 | Implantação e suporte | S | S | S | S | S | Docker Compose | Idem | Falta importação de saldos e históricos de outros sistemas | P2 | M |
| 24 | Preço público | P | NV | NV | NV | NV | — | — | Decisão comercial | — | — |

Complexidade: B = baixa, M = média, A = alta.

## 2. Conclusões

**Essenciais para competir (estado atual):**
- **Atendidos nesta etapa:** reservas; portaria; conciliação OFX; retorno CNAB; fechamento mensal.
- **Pendentes:** registro de boleto/PIX com banco homologado; votação online; app instalável (PWA).

**Diferenciais do Tek:**
- Rateio por fração ideal explícito.
- Controle de procurações.
- Isolamento multiempresa e LGPD comprovados por teste.
- Bloqueio de período com trilha de reabertura.
- Conciliação "só quando inequívoca", com explicação.
- Indicadores que não inventam dados.

**Pontos fortes já existentes:**
- Integridade financeira.
- Autorização granular por condomínio.
- Portal restrito à própria unidade.
- 13 relatórios com exportação PDF/Excel/CSV.

**Riscos:** ver [AUDITORIA.md §4](AUDITORIA.md) (datas em UTC nos módulos antigos, sem git, sem CI, Docker não testado).

**Oportunidades em Londrina:**
- A Sibrax é o único fornecedor com sede local. A Superlógica predomina via administradoras (ASA, Premium, Euro). Nenhum nacional mostra suporte local.
- Proposta: suporte e implantação presenciais em Londrina + homologação prioritária com os bancos mais usados por condomínios da região (a confirmar com clientes; Sicoob e Sicredi são hipóteses **não verificadas**).
- Preço público por faixa de unidades, seguindo o padrão Conviver/BRCondomínio.
- Transparência para o conselho: fechamento com fotografia dos saldos e indicadores.

## 3. Backlog técnico priorizado

| ID | Funcionalidade | Problema que resolve | Critérios de aceite | Prior. | Dependências | Arquivos/componentes | Testes |
|---|---|---|---|---|---|---|---|
| B1 | ✅ Fechamento mensal | Alterações retroativas em contas já prestadas | Fecha só fim de mês já encerrado; bloqueia baixa, estorno, transferência, conciliação e lançamento até a data; reabertura com justificativa e auditoria | P2 | — | `V7`, `PeriodLock`, `PeriodClosingService`, `closing-tab.ts` | `PeriodClosingIT` |
| B2 | ✅ Importação OFX + conciliação | Conferência manual linha a linha | Arquivo e FITID idempotentes; casa só o inequívoco (±3 dias, valor igual); casar, desfazer, ignorar e lançar tarifa | P2 | — | `V8`, `OfxParser`, `StatementMatcher`, `StatementImportService`, `reconciliation-tab.ts` | `OfxParserTest`, `StatementMatcherTest`, `StatementImportIT` |
| B3 | ✅ Retorno CNAB 240 | Baixa manual das cobranças | Localiza pelo número de cobrança; separa multa e juros; não baixa duas vezes; pendências tratáveis; banco do arquivo = banco da conta | P1 | Homologação por banco | `V9`, `Cnab240ReturnParser`, `BankReturnService`, `bank-return-tab.ts` | `Cnab240ReturnParserTest`, `BankReturnIT` |
| B4 | ✅ Reservas | Ausência de recurso obrigatório de mercado | Sem sobreposição (serviço + exclusão no banco); regras por área; taxa vira cobrança; portal | P3 | — | `V10`, `reservation/*`, `reservation-page.ts`, `portal-reservations.ts` | `ReservationIT` |
| B5 | ✅ Portaria | Ausência de recurso obrigatório | Código de uso único; portaria não vê o código; aviso ao morador; documento mascarado; anonimização | P3 | — | `V11`, `visitor/*`, `PrivacyRetentionJob`, `gate-page.ts`, `portal-visitors.ts` | `VisitorIT` |
| B6 | ✅ Indicadores | Decisão sem visão de futuro | Projeção pelos vencimentos (vencidos à parte); outlier só com histórico ≥ 3 meses | P5 | — | `insight/*`, `insights-tab.ts` | `InsightCalculatorTest`, `InsightIT` |
| B7 | ✅ Limite por IP na autenticação | Força bruta distribuída | 429 + Retry-After após N tentativas por IP e janela | P1 | — | `AuthRateLimitFilter` | `AuthRateLimitFilterTest` |
| B8 | Registro de boleto/PIX via API bancária | Boleto emitido fora do sistema | Porta `CollectionProvider`; adaptador por banco; webhook de liquidação idempotente; sandbox do banco | **P1** | Contrato e credenciais de banco/PSP | `integration/bank`, `billing` | Contrato contra sandbox |
| B9 | Datas de negócio no fuso do condomínio | "Hoje" errado entre 21h e 0h | Trocar `LocalDate.now(clock)` por um relógio com zona; revisar cada uso | P1 | — | `ClockConfig` e todos os serviços | Teste com relógio fixo às 22h BRT |
| B10 | Votação online em assembleia | Assembleia virtual | Voto do morador autenticado, 1 por unidade e item, janela de votação, trilha; aviso de validade jurídica | P2 | Validação jurídica da convenção | `assembly`, portal | IT de votação, peso por fração |
| B11 | Remessa CNAB 240/400 | Registrar boletos por arquivo | Gerar remessa por banco homologado | P2 | Layout do banco | `integration/bank` | Testes de layout |
| B12 | Régua de cobrança | Inadimplência | Lembretes D-3, D+1, D+15 por e-mail e notificação; registro no histórico de cobrança | P2 | E-mail SMTP | `jobs`, `billing` | IT com relógio fixo |
| B13 | PWA do portal | App do morador | Manifest + service worker; instalável | P2 | — | `frontend` | Lighthouse |
| B14 | Exportação contábil | Integração com escritório contábil | Exportar lançamentos no layout do sistema contábil-alvo | P2 | Escolha do sistema contábil | `report` | Teste de layout |
| B15 | Git + CI (testes, `npm audit`, OWASP DC) | Processo | Pipeline verde a cada mudança | P1 | Repositório remoto | raiz | — |
| B16 | Row-Level Security | Defesa em profundidade | Políticas por `organization_id` com `SET app.org` | P3 | — | migração + `DataSource` | `TenantIsolationIT` |
| B17 | Assistente de IA (consulta) | Dúvidas operacionais | Somente leitura, ferramentas com escopo do usuário, cita a fonte, sem executar operações | P3 | Contrato com provedor de IA, DPA/LGPD | novo módulo | Avaliação de respostas |

## 4. Avaliação: assistente de IA

**Viável tecnicamente, mas não foi implementado nesta etapa.** Três motivos:

1. Exige escolher e contratar um provedor, com acordo de tratamento de dados. Enviar dados financeiros e pessoais de moradores a um terceiro é tratamento de dados sob a LGPD.
2. Exige chave de API, inexistente no ambiente.
3. A exigência de "não inventar informações" pede respostas ancoradas em ferramentas de consulta que já respeitem `CondominiumAccess`.

**Desenho recomendado:**
- o modelo só chama endpoints de leitura já existentes (relatórios, inadimplência, indicadores) com o token do próprio usuário;
- cada resposta cita os números retornados pela ferramenta;
- nenhuma ferramenta de escrita;
- registro em auditoria de cada consulta.

Os indicadores implementados (B6) cobrem a parte analítica sem esses riscos.
