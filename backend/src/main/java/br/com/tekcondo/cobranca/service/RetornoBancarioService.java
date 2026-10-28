package br.com.tekcondo.cobranca.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.cobranca.entity.ArquivoRetorno;
import br.com.tekcondo.cobranca.entity.ItemRetorno;
import br.com.tekcondo.cobranca.repository.ArquivoRetornoRepository;
import br.com.tekcondo.cobranca.repository.ItemRetornoRepository;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoDetalhe;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.financeiro.service.LancamentoService;
import br.com.tekcondo.financeiro.service.ConfiguracaoFinanceiraService;
import br.com.tekcondo.financeiro.service.EncargosCalculator;
import br.com.tekcondo.financeiro.service.BloqueioPeriodo;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.banco.RetornoCnab240Parser;
import br.com.tekcondo.integracao.banco.OfxParser;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa o arquivo de retorno de cobrança (CNAB 240): cada liquidação é localizada pelo número de cobrança do
 * título e baixada automaticamente, com multa/juros separados conforme os parâmetros fotografados no título. O que
 * não puder ser baixado com segurança fica pendente para tratamento manual — nada é "forçado".
 */
@Service
public class RetornoBancarioService {

    public record ResumoArquivo(UUID id, UUID idContaBancaria, String nomeArquivo, String codigoBanco, LocalDate dataGeracao, int totalItens,
                                int baixados, int pendentes, Instant criadoEm, String nomeCriadoPor) {
    }

    public record ItemRetornoResponse(UUID id, UUID idArquivo, int sequencia, String codigoMovimento, String descricaoMovimento,
                                      String nossoNumero, String seuNumero, LocalDate dataVencimento, BigDecimal valorTitulo,
                                      BigDecimal encargos, BigDecimal desconto, BigDecimal valorPago, BigDecimal tarifa,
                                      LocalDate dataOcorrencia, LocalDate dataCredito, ItemRetorno.Situacao situacao, UUID idLancamento,
                                      String descricaoLancamento, String rotuloUnidade, String mensagem) {
    }

    public record BaixarItemRequest(@NotNull(message = "Informe a cobrança.") UUID idLancamento) {
    }

    public record IgnorarItemRequest(@NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 255) String observacao) {
    }

    private final ArquivoRetornoRepository arquivoRepository;
    private final ItemRetornoRepository itemRepository;
    private final LancamentoRepository lancamentoRepository;
    private final LancamentoService lancamentoService;
    private final ConfiguracaoFinanceiraService configuracao;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public RetornoBancarioService(ArquivoRetornoRepository arquivoRepository, ItemRetornoRepository itemRepository,
                                  LancamentoRepository lancamentoRepository, LancamentoService lancamentoService,
                                  ConfiguracaoFinanceiraService configuracao, BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso,
                                  AuditoriaService auditoria, Clock clock) {
        this.arquivoRepository = arquivoRepository;
        this.itemRepository = itemRepository;
        this.lancamentoRepository = lancamentoRepository;
        this.lancamentoService = lancamentoService;
        this.configuracao = configuracao;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional
    public ResumoArquivo processar(UUID idCondominio, UUID idContaBancaria, String nomeArquivo, byte[] conteudo) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.BILLING_EDIT);
        acesso.exigir(idCondominio, Permissao.FINANCE_EDIT);
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, idContaBancaria);
        if (!conta.isAtivo()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        String hash = OfxParser.sha256(conteudo);
        if (arquivoRepository.existsByIdContaBancariaAndHashArquivo(idContaBancaria, hash)) {
            throw ApiException.conflito("RETURN_ALREADY_PROCESSED", "Este arquivo de retorno já foi processado nesta conta.");
        }
        RetornoCnab240Parser.Retorno retorno = RetornoCnab240Parser.parse(conteudo);
        if (conta.getCodigoBanco() != null && !semZeros(conta.getCodigoBanco()).equals(semZeros(retorno.codigoBanco()))) {
            throw ApiException.regraNegocio("RETURN_BANK_MISMATCH", "O retorno é do banco " + retorno.codigoBanco()
                    + ", mas a conta " + conta.getNome() + " é do banco " + conta.getCodigoBanco() + ".");
        }
        var usuario = acesso.usuario();
        ArquivoRetorno arquivo = arquivoRepository.save(new ArquivoRetorno(condominio.getIdAdministradora(), idCondominio, idContaBancaria,
                nomeSeguro(nomeArquivo), hash, retorno.codigoBanco(), retorno.dataGeracao(), retorno.itens().size(), usuario.idUsuario(), usuario.nome()));
        int baixados = 0;
        int pendentes = 0;
        for (RetornoCnab240Parser.Item itemLido : retorno.itens()) {
            ItemRetorno item = new ItemRetorno(condominio.getIdAdministradora(), idCondominio, idContaBancaria, arquivo.getId(), itemLido);
            if (itemLido.isLiquidacao()) {
                if (itemRepository.jaBaixado(idContaBancaria, item.getCodigoMovimento(), item.getDataOcorrencia(), item.getNossoNumero(), item.getSeuNumero())) {
                    item.pendente(null, "Liquidação já baixada em retorno anterior (não baixada de novo).");
                } else {
                    Optional<Lancamento> lancamento = localizar(idCondominio, item);
                    if (lancamento.isEmpty()) {
                        item.pendente(null, "Cobrança não localizada pelo número informado ao banco.");
                    } else {
                        baixar(idCondominio, conta, item, lancamento.get());
                    }
                }
                if (item.getSituacao() == ItemRetorno.Situacao.SETTLED) baixados++;
                else pendentes++;
            } else {
                item.informativo(descricaoMovimento(item.getCodigoMovimento()));
            }
            itemRepository.save(item);
        }
        arquivo.registrarResultado(baixados, pendentes);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("file", arquivo.getNomeArquivo());
        detalhes.put("bank", retorno.codigoBanco());
        detalhes.put("items", retorno.itens().size());
        detalhes.put("settled", baixados);
        detalhes.put("pending", pendentes);
        auditoria.registrar(AcaoAuditoria.BANK_RETURN_PROCESSED, "BankAccount", idContaBancaria, detalhes);
        return paraResumo(arquivo);
    }

    @Transactional(readOnly = true)
    public List<ResumoArquivo> arquivos(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        return arquivoRepository.findTop30ByIdCondominioOrderByCriadoEmDesc(idCondominio).stream().map(RetornoBancarioService::paraResumo).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemRetornoResponse> itens(UUID idCondominio, UUID idArquivo) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        ArquivoRetorno arquivo = arquivoRepository.findById(idArquivo).filter(f -> arquivoPertence(f, idCondominio))
                .orElseThrow(() -> ApiException.naoEncontrado("Arquivo de retorno"));
        return itemRepository.findByIdArquivoOrderBySequenciaAsc(arquivo.getId()).stream().map(this::paraResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ItemRetornoResponse> pendentes(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.BILLING_VIEW);
        return itemRepository.buscarPorSituacao(idCondominio, ItemRetorno.Situacao.PENDING).stream().map(this::paraResponse).toList();
    }

    /** Baixa manual de uma pendência na cobrança escolhida pelo usuário (ex.: pagador usou boleto antigo). */
    @Transactional
    public ItemRetornoResponse baixarManualmente(UUID idCondominio, UUID idItem, BaixarItemRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.BILLING_EDIT);
        acesso.exigir(idCondominio, Permissao.FINANCE_EDIT);
        ItemRetorno item = carregar(idCondominio, idItem);
        if (item.getSituacao() != ItemRetorno.Situacao.PENDING) {
            throw ApiException.regraNegocio("RETURN_ITEM_NOT_PENDING", "Este item do retorno não está pendente.");
        }
        if (itemRepository.jaBaixado(item.getIdContaBancaria(), item.getCodigoMovimento(), item.getDataOcorrencia(),
                item.getNossoNumero(), item.getSeuNumero())) {
            throw ApiException.conflito("RETURN_ALREADY_SETTLED", "Esta liquidação já foi baixada em outro retorno.");
        }
        Lancamento lancamento = lancamentoService.carregar(idCondominio, request.idLancamento());
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, item.getIdContaBancaria());
        baixar(idCondominio, conta, item, lancamento);
        if (item.getSituacao() != ItemRetorno.Situacao.SETTLED) {
            throw ApiException.regraNegocio("RETURN_ITEM_NOT_SETTLED", item.getMensagem());
        }
        auditoria.registrar(AcaoAuditoria.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", idItem, Map.of("entryId", lancamento.getId().toString()));
        return paraResponse(item);
    }

    @Transactional
    public ItemRetornoResponse ignorar(UUID idCondominio, UUID idItem, IgnorarItemRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.BILLING_EDIT);
        ItemRetorno item = carregar(idCondominio, idItem);
        item.ignorar(request.observacao().trim(), acesso.usuario().idUsuario(), Instant.now(clock));
        auditoria.registrar(AcaoAuditoria.BANK_RETURN_ITEM_RESOLVED, "BankReturnItem", idItem, Map.of("ignored", request.observacao().trim()));
        return paraResponse(item);
    }

    // ------------------------------------------------------------------

    private Optional<Lancamento> localizar(UUID idCondominio, ItemRetorno item) {
        for (String candidato : new String[]{item.getSeuNumero(), item.getNossoNumero()}) {
            Long numero = numero(candidato);
            if (numero != null) {
                Optional<Lancamento> encontrado = lancamentoRepository.findByIdCondominioAndNumeroCobranca(idCondominio, numero)
                        .filter(e -> e.getNatureza() == Lancamento.Natureza.INCOME);
                if (encontrado.isPresent()) {
                    return encontrado;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Todas as regras são verificadas ANTES da baixa: uma exceção dentro da transação a marcaria para rollback e
     * perderia o processamento do arquivo inteiro. O que não passar vira pendência com o motivo.
     */
    private void baixar(UUID idCondominio, ContaBancaria conta, ItemRetorno item, Lancamento lancamento) {
        LocalDate hoje = LocalDate.now(clock);
        LocalDate data = item.dataLiquidacao();
        if (data != null && data.isAfter(hoje)) {
            data = item.getDataOcorrencia();
        }
        LocalDate fechadoAte = bloqueioPeriodo.fechadoAte(idCondominio);
        String problema = null;
        if (lancamento.getSituacao() != Lancamento.Situacao.OPEN && lancamento.getSituacao() != Lancamento.Situacao.PARTIAL) {
            problema = "A cobrança nº " + lancamento.getNumeroCobranca() + " não está em aberto (" + lancamento.getSituacao() + ").";
        } else if (data == null || data.isAfter(hoje)) {
            problema = "Retorno sem data de pagamento válida.";
        } else if (data.isBefore(conta.getDataAbertura())) {
            problema = "Data de pagamento anterior à abertura da conta bancária.";
        } else if (fechadoAte != null && !data.isAfter(fechadoAte)) {
            problema = "Pagamento em período financeiro fechado; reabra o período para baixar.";
        }
        BigDecimal principal = item.getValorPago().subtract(item.getEncargos()).add(item.getDesconto());
        BigDecimal encargos = item.getEncargos();
        if (problema == null && principal.signum() <= 0) {
            problema = "Valor pago no retorno não cobre o principal.";
        }
        if (problema != null) {
            item.pendente(lancamento.getId(), problema);
            return;
        }
        String observacao = "Retorno CNAB — seq. " + item.getSequencia();
        if (principal.compareTo(lancamento.valorAberto()) > 0) {
            // Pago a maior: o excedente é tratado como encargo recebido, mantendo o principal igual ao saldo do título.
            encargos = encargos.add(principal.subtract(lancamento.valorAberto()));
            principal = lancamento.valorAberto();
            observacao += " (pago a maior)";
        }
        EncargosCalculator.Calculo calculo = EncargosCalculator.calcular(principal, lancamento.getDataVencimento(), data,
                lancamento.getPercentualMulta(), lancamento.getPercentualJurosMensal());
        BigDecimal multa = encargos.min(calculo.multa());
        BigDecimal juros = encargos.subtract(multa);
        LancamentoDetalhe detalhe = lancamentoService.baixar(idCondominio, lancamento.getId(), new BaixaRequest(conta.getId(), data, principal,
                multa, juros, item.getDesconto(), Baixa.FormaPagamento.BOLETO, observacao));
        item.baixado(lancamento.getId(), detalhe.baixas().getLast().id());
    }

    private ItemRetorno carregar(UUID idCondominio, UUID idItem) {
        return itemRepository.buscarNoCondominio(idItem, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Item do retorno"));
    }

    private static boolean arquivoPertence(ArquivoRetorno f, UUID idCondominio) {
        return f.getIdCondominio().equals(idCondominio);
    }

    private ItemRetornoResponse paraResponse(ItemRetorno i) {
        Lancamento e = i.getIdLancamento() == null ? null : lancamentoRepository.findById(i.getIdLancamento()).orElse(null);
        return new ItemRetornoResponse(i.getId(), i.getIdArquivo(), i.getSequencia(), i.getCodigoMovimento(), descricaoMovimento(i.getCodigoMovimento()),
                i.getNossoNumero(), i.getSeuNumero(), i.getDataVencimento(), i.getValorTitulo(), i.getEncargos(), i.getDesconto(), i.getValorPago(),
                i.getTarifa(), i.getDataOcorrencia(), i.getDataCredito(), i.getSituacao(), i.getIdLancamento(),
                e == null ? null : e.getDescricao(), e == null || e.getUnidade() == null ? null : e.getUnidade().rotulo(), i.getMensagem());
    }

    private static ResumoArquivo paraResumo(ArquivoRetorno f) {
        return new ResumoArquivo(f.getId(), f.getIdContaBancaria(), f.getNomeArquivo(), f.getCodigoBanco(), f.getDataGeracao(), f.getTotalItens(),
                f.getBaixados(), f.getPendentes(), f.getCriadoEm(), f.getNomeCriadoPor());
    }

    /** Códigos de movimento de retorno mais comuns (FEBRABAN, nota G044). */
    static String descricaoMovimento(String codigo) {
        return switch (codigo) {
            case "02" -> "Entrada confirmada";
            case "03" -> "Entrada rejeitada";
            case "06" -> "Liquidação";
            case "09" -> "Baixa";
            case "12" -> "Abatimento concedido";
            case "14" -> "Vencimento alterado";
            case "17" -> "Liquidação após baixa";
            case "19" -> "Protesto confirmado";
            case "28" -> "Débito de tarifas/custas";
            default -> "Movimento " + codigo;
        };
    }

    private static Long numero(String s) {
        if (s == null) return null;
        String digitos = s.replaceAll("\\D", "").replaceFirst("^0+", "");
        if (digitos.isEmpty() || digitos.length() > 18) return null;
        return Long.parseLong(digitos);
    }

    private static String semZeros(String banco) {
        String d = banco.replaceAll("\\D", "").replaceFirst("^0+", "");
        return d.isEmpty() ? banco : d;
    }

    private static String nomeSeguro(String nome) {
        String n = nome == null || nome.isBlank() ? "retorno.ret" : nome.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
