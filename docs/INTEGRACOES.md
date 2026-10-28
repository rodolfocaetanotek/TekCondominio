# Integrações e dependências externas

Situação em 28/10/2026. **Nenhuma integração bancária está homologada com banco real.** O que existe são
adaptadores de arquivo nos layouts padrão, testados com arquivos gerados conforme a especificação. Cada banco
deve ser homologado antes do uso em produção.

## 1. Extrato bancário — OFX (implementado)

| Item | Detalhe |
|---|---|
| Código | `integration/bank/OfxParser.java`, `financeiro/service/ImportacaoExtratoService.java`, `ConciliacaoExtratoMatcher.java` |
| Endpoint | `POST /api/v1/condominios/{idCondominio}/financeiro/contas-bancarias/{idConta}/extratos` (multipart `arquivo`) |
| Formatos | OFX 1.x (SGML) e 2.x (XML); charset 1252 ou UTF-8; valor com ponto ou vírgula decimal |
| Idempotência | hash SHA-256 do arquivo por conta (409 se repetido); `FITID` único por conta (extratos sobrepostos não duplicam) |
| Conferência | o número da conta no arquivo (`ACCTID`) é comparado ao cadastro. Se divergir, responde 422, a menos que `ignorarConferenciaConta=true`. |
| Conciliação automática | apenas pares inequívocos: mesmo valor com sinal, até 3 dias de diferença, um candidato para a linha e uma linha para o candidato |
| Depende de terceiros | Não. O usuário baixa o OFX no internet banking. |
| Validar por banco | exportação OFX de cada banco usado: datas, sinal e FITID estável entre exportações. Alguns bancos geram FITID instável; nesse caso a deduplicação usa o FITID tal como vem. |

## 2. Retorno de cobrança — CNAB 240 FEBRABAN (implementado)

| Item | Detalhe |
|---|---|
| Código | `integration/bank/Cnab240ReturnParser.java`, `billing/service/BankReturnService.java` |
| Endpoint | `POST /api/v1/condominios/{c}/billing/bank-returns?bankAccountId=…` (multipart `file`) |
| Layout | registro 0 (header), segmentos T e U (detalhe), FEBRABAN v10. Posições usadas: T 16-17 (movimento), 38-57 (nosso nº), 59-73 (seu nº), 74-81 (vencimento), 82-96 (valor), 199-213 (tarifa); U 18-32 (juros/multa), 33-47 (desconto), 48-62 (abatimento), 78-92 (pago), 138-145 (ocorrência), 146-153 (crédito). |
| Identificação | `financial_entry.collection_number` (sequência do banco de dados), procurado primeiro no "seu número" e depois no nosso número. **Ao registrar o boleto no banco, informe esse número no campo "seu número/número do documento".** Ele aparece em cada título (`collectionNumber` na API). |
| Baixa | liquidação (movimentos 06 e 17); data = data de crédito (ou ocorrência). Multa até o valor calculado pelo título e o restante como juros. Pagamento a maior é registrado como encargo. Forma de pagamento BOLETO. |
| Segurança contra duplicidade | hash do arquivo + verificação do mesmo movimento já baixado + índice único parcial no banco |
| Pendências | título não localizado, já pago, período fechado ou valor insuficiente ficam **pendentes** com o motivo, para baixa manual ou descarte com justificativa |
| Não implementado | **CNAB 400**; remessa (geração do arquivo de registro); tarifas do retorno não são lançadas automaticamente (registre pela conciliação OFX) |
| Depende de terceiros | convênio de cobrança do condomínio com o banco e **homologação do layout** com o banco (variações em nosso nº/DV e códigos de movimento) |

## 3. Registro de boleto / PIX via API (não implementado — depende de banco)

Pedir ao banco ou PSP: contrato de cobrança, credenciais OAuth2/mTLS, ambiente de sandbox e documentação da API
de boletos e PIX (cobrança com vencimento, `cobv`).

Arquitetura prevista:
- porta `CollectionProvider` no módulo `billing` com `register(charge)`, `cancel(charge)` e `status(charge)`;
- um adaptador por banco em `integration/bank/<banco>`;
- webhook de liquidação reutilizando a mesma rotina de baixa do retorno CNAB (`BankReturnService.settle`), que já é idempotente.

Enquanto isso, o fluxo suportado é: registrar o boleto no banco (portal ou remessa) com o número de cobrança e
importar o retorno.

## 4. E-mail (existente)

SMTP via `TEKCONDO_SMTP_*`. Em desenvolvimento (`TEKCONDO_MAIL_PROVIDER=file`) as mensagens são gravadas em
`.local-dev/outbox`. O envio real por SMTP não foi testado nesta máquina.

## 5. Armazenamento de arquivos (existente)

Porta `FileStorage`, com implementação em disco local (`TEKCONDO_STORAGE_DIR`). S3 não implementado.

## 6. Extensões do PostgreSQL

`btree_gist` (V10) é usada na restrição de exclusão que impede reservas sobrepostas. É uma extensão *trusted*
desde o PostgreSQL 13: o dono do banco pode criá-la sem superusuário. Está presente na imagem `postgres:16-alpine`.

## 7. Dependências externas pendentes

| Dependência | Para quê | Responsável |
|---|---|---|
| Convênio de cobrança + homologação CNAB por banco | baixa automática em produção | administradora + banco |
| API bancária/PSP (boleto + PIX) | registro e liquidação em tempo real | contrato comercial |
| Provedor de IA + DPA | assistente de consulta (avaliado, não implementado) | decisão comercial e jurídica |
| Parecer jurídico sobre assembleia virtual | votação online | jurídico da administradora |
| Sistema contábil-alvo | exportação contábil | definição comercial |
