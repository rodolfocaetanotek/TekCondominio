package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.BaixaResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.CalculoEncargos;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoDetalhe;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.LancamentoResumo;
import br.com.tekcondo.financeiro.entity.Baixa;
import br.com.tekcondo.financeiro.entity.CentroCusto;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.BaixaRepository;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.repository.PessoaRepository;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import br.com.tekcondo.supplier.entity.Supplier;
import br.com.tekcondo.supplier.repository.SupplierRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contas a pagar e a receber: lançamento, aprovação, baixa, estorno e cancelamento. */
@Service
public class LancamentoService {

    /** Tipo de entidade gravado na auditoria (dado histórico, continua em inglês). */
    private static final String ENTIDADE = "FinancialEntry";

    private final LancamentoRepository lancamentoRepository;
    private final BaixaRepository baixaRepository;
    private final SupplierRepository supplierRepository;
    private final UnidadeRepository unidadeRepository;
    private final PessoaRepository pessoaRepository;
    private final ConfiguracaoFinanceiraService configuracao;
    private final BloqueioPeriodo bloqueioPeriodo;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public LancamentoService(LancamentoRepository lancamentoRepository, BaixaRepository baixaRepository,
                             SupplierRepository supplierRepository, UnidadeRepository unidadeRepository,
                             PessoaRepository pessoaRepository, ConfiguracaoFinanceiraService configuracao,
                             BloqueioPeriodo bloqueioPeriodo, AcessoCondominio acesso, AuditoriaService auditoriaService,
                             Clock clock) {
        this.lancamentoRepository = lancamentoRepository;
        this.baixaRepository = baixaRepository;
        this.supplierRepository = supplierRepository;
        this.unidadeRepository = unidadeRepository;
        this.pessoaRepository = pessoaRepository;
        this.configuracao = configuracao;
        this.bloqueioPeriodo = bloqueioPeriodo;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    public record Filtro(Lancamento.Natureza natureza, List<Lancamento.Situacao> situacoes, LocalDate vencimentoDe,
                         LocalDate vencimentoAte, UUID idContaPlano, UUID idFornecedor, UUID idUnidade, String busca,
                         Boolean vencido, Lancamento.Aprovacao aprovacao) {
    }

    @Transactional(readOnly = true)
    public PaginaResponse<LancamentoResumo> listar(UUID idCondominio, Filtro f, Pageable pageable) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        LocalDate hoje = LocalDate.now(clock);
        Specification<Lancamento> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("idCondominio"), idCondominio));
            if (f.natureza() != null) p.add(cb.equal(root.get("natureza"), f.natureza()));
            if (f.situacoes() != null && !f.situacoes().isEmpty()) p.add(root.get("situacao").in(f.situacoes()));
            if (f.vencimentoDe() != null) p.add(cb.greaterThanOrEqualTo(root.get("dataVencimento"), f.vencimentoDe()));
            if (f.vencimentoAte() != null) p.add(cb.lessThanOrEqualTo(root.get("dataVencimento"), f.vencimentoAte()));
            if (f.idContaPlano() != null) p.add(cb.equal(root.get("contaPlano").get("id"), f.idContaPlano()));
            if (f.idFornecedor() != null) p.add(cb.equal(root.get("fornecedor").get("id"), f.idFornecedor()));
            if (f.idUnidade() != null) p.add(cb.equal(root.get("unidade").get("id"), f.idUnidade()));
            if (f.aprovacao() != null) p.add(cb.equal(root.get("aprovacao"), f.aprovacao()));
            if (Boolean.TRUE.equals(f.vencido())) {
                p.add(root.get("situacao").in(Lancamento.Situacao.OPEN, Lancamento.Situacao.PARTIAL));
                p.add(cb.lessThan(root.get("dataVencimento"), hoje));
            }
            if (f.busca() != null && !f.busca().isBlank()) {
                String like = "%" + f.busca().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("descricao")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("numeroDocumento"), "")), like)));
            }
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("contaPlano");
                root.fetch("fornecedor", JoinType.LEFT);
                root.fetch("unidade", JoinType.LEFT).fetch("bloco", JoinType.LEFT);
                root.fetch("pessoa", JoinType.LEFT);
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(lancamentoRepository.findAll(spec, pageable), e -> resumo(e, hoje));
    }

    @Transactional(readOnly = true)
    public LancamentoDetalhe obter(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return detalhe(carregar(idCondominio, id));
    }

    /** Cria o lançamento (ou a série, quando {@code repeticoes} > 1: parcelas mensais da mesma despesa/receita). */
    @Transactional
    public List<LancamentoDetalhe> cadastrar(UUID idCondominio, LancamentoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        UUID idUsuario = acesso.usuario().idUsuario();
        int repeticoes = request.repeticoes() == null ? 1 : request.repeticoes();
        UUID grupo = repeticoes > 1 ? UUID.randomUUID() : null;
        Referencias refs = resolver(idCondominio, request);
        List<Lancamento> criados = new ArrayList<>();
        for (int i = 0; i < repeticoes; i++) {
            Lancamento lancamento = new Lancamento(condominio.getIdAdministradora(), idCondominio, request.natureza(),
                    repeticoes > 1 ? Lancamento.Origem.RECURRENCE : Lancamento.Origem.MANUAL, idUsuario);
            LocalDate vencimento = request.dataVencimento().plusMonths(i);
            LocalDate competencia = (request.competencia() == null ? request.dataVencimento() : request.competencia()).plusMonths(i);
            String descricao = repeticoes > 1 ? request.descricao().trim() + " (" + (i + 1) + "/" + repeticoes + ")" : request.descricao();
            String documento = repeticoes > 1 && request.numeroDocumento() != null
                    ? request.numeroDocumento().trim() + "-" + (i + 1)
                    : emBranco(request.numeroDocumento());
            lancamento.atualizar(descricao, refs.conta, refs.centroCusto, refs.fornecedor, refs.unidade, refs.pessoa,
                    request.valor(), vencimento, competencia, documento, emBranco(request.observacoes()), idUsuario);
            if (grupo != null) {
                lancamento.recorrencia(grupo, i + 1, repeticoes);
            }
            lancamento.exigirAprovacao(request.natureza() == Lancamento.Natureza.EXPENSE && condominio.isExigeAprovacaoContasPagar());
            if (request.natureza() == Lancamento.Natureza.INCOME && refs.unidade != null) {
                lancamento.definirEncargos(condominio.getPercentualMulta(), condominio.getPercentualJurosMensal());
            }
            exigirDocumentoUnico(idCondominio, lancamento);
            bloqueioPeriodo.exigirAberto(idCondominio, lancamento.getCompetencia());
            lancamentoRepository.save(lancamento);
            auditoriaService.registrar(AcaoAuditoria.ENTRY_CREATED, ENTIDADE, lancamento.getId(), detalhesAuditoria(lancamento));
            criados.add(lancamento);
        }
        lancamentoRepository.flush(); // o número de cobrança é gerado pelo banco no INSERT
        return criados.stream().map(this::detalhe).toList();
    }

    @Transactional
    public LancamentoDetalhe atualizar(UUID idCondominio, UUID id, LancamentoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        Lancamento lancamento = carregar(idCondominio, id);
        if (request.versao() == null || !request.versao().equals(lancamento.getVersao())) {
            throw ApiException.versaoObsoleta();
        }
        if (request.natureza() != lancamento.getNatureza()) {
            throw ApiException.regraNegocio("ENTRY_KIND_IMMUTABLE", "Não é possível trocar receita por despesa. Cancele e lance novamente.");
        }
        bloqueioPeriodo.exigirAberto(idCondominio, lancamento.getCompetencia(),
                request.competencia() == null ? request.dataVencimento() : request.competencia());
        Map<String, Object> antes = detalhesAuditoria(lancamento);
        BigDecimal valorAnterior = lancamento.getValor();
        Referencias refs = resolver(idCondominio, request);
        lancamento.atualizar(request.descricao(), refs.conta, refs.centroCusto, refs.fornecedor, refs.unidade, refs.pessoa,
                request.valor(), request.dataVencimento(),
                request.competencia() == null ? request.dataVencimento() : request.competencia(),
                emBranco(request.numeroDocumento()), emBranco(request.observacoes()), acesso.usuario().idUsuario());
        // Despesa já aprovada cujo valor mudou volta para aprovação.
        if (lancamento.getNatureza() == Lancamento.Natureza.EXPENSE && condominio.isExigeAprovacaoContasPagar()
                && lancamento.getAprovacao() == Lancamento.Aprovacao.APPROVED && valorAnterior.compareTo(request.valor()) != 0) {
            lancamento.exigirAprovacao(true);
        }
        exigirDocumentoUnico(idCondominio, lancamento);
        lancamentoRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.ENTRY_UPDATED, ENTIDADE, id, Map.of("from", antes, "to", detalhesAuditoria(lancamento)));
        return detalhe(lancamento);
    }

    @Transactional
    public LancamentoDetalhe decidirAprovacao(UUID idCondominio, UUID id, boolean aprovado, String observacao) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_APPROVE);
        Lancamento lancamento = carregar(idCondominio, id);
        if (aprovado) {
            lancamento.aprovar(acesso.usuario().idUsuario(), Instant.now(clock), emBranco(observacao));
        } else {
            if (observacao == null || observacao.isBlank()) {
                throw ApiException.requisicaoInvalida("REASON_REQUIRED", "Informe o motivo da reprovação.");
            }
            lancamento.reprovar(acesso.usuario().idUsuario(), Instant.now(clock), observacao.trim());
        }
        lancamentoRepository.flush();
        auditoriaService.registrar(aprovado ? AcaoAuditoria.ENTRY_APPROVED : AcaoAuditoria.ENTRY_REJECTED, ENTIDADE, id,
                observacao == null ? null : Map.of("note", observacao));
        return detalhe(lancamento);
    }

    @Transactional
    public LancamentoDetalhe cancelar(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.FINANCE_CANCEL);
        Lancamento lancamento = carregar(idCondominio, id);
        bloqueioPeriodo.exigirAberto(idCondominio, lancamento.getCompetencia());
        lancamento.cancelar(motivo.trim(), acesso.usuario().idUsuario(), Instant.now(clock));
        lancamentoRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.ENTRY_CANCELLED, ENTIDADE, id, Map.of("reason", motivo.trim(),
                "amount", lancamento.getValor().toPlainString()));
        return detalhe(lancamento);
    }

    @Transactional(readOnly = true)
    public CalculoEncargos calcularEncargos(UUID idCondominio, UUID id, LocalDate data) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        Lancamento lancamento = carregar(idCondominio, id);
        EncargosCalculator.Calculo c = EncargosCalculator.calcular(lancamento.valorAberto(), lancamento.getDataVencimento(),
                data == null ? LocalDate.now(clock) : data, lancamento.getPercentualMulta(), lancamento.getPercentualJurosMensal());
        return new CalculoEncargos(c.principal(), c.diasAtraso(), c.multa(), c.juros(), c.total());
    }

    /** Baixa total ou parcial. Concorrência protegida pela versão do título (duas baixas simultâneas → 409). */
    @Transactional
    public LancamentoDetalhe baixar(UUID idCondominio, UUID id, BaixaRequest request) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        Lancamento lancamento = carregar(idCondominio, id);
        if (request.dataPagamento().isAfter(LocalDate.now(clock))) {
            throw ApiException.regraNegocio("FUTURE_PAYMENT", "A data do pagamento não pode ser futura.");
        }
        bloqueioPeriodo.exigirAberto(idCondominio, request.dataPagamento());
        ContaBancaria conta = configuracao.carregarContaBancaria(idCondominio, request.idContaBancaria());
        if (!conta.isAtivo()) {
            throw ApiException.regraNegocio("BANK_ACCOUNT_INACTIVE", "A conta bancária está inativa.");
        }
        if (request.dataPagamento().isBefore(conta.getDataAbertura())) {
            throw ApiException.regraNegocio("PAYMENT_BEFORE_OPENING", "A data é anterior à abertura da conta bancária.");
        }
        lancamento.registrarBaixa(request.principal(), acesso.usuario().idUsuario());
        Baixa baixa = baixaRepository.save(new Baixa(lancamento, conta, request.dataPagamento(), request.principal(),
                zero(request.multa()), zero(request.juros()), zero(request.desconto()), request.formaPagamento(),
                emBranco(request.observacoes()), acesso.usuario().idUsuario()));
        lancamentoRepository.flush();
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("paymentId", baixa.getId().toString());
        detalhes.put("principal", baixa.getPrincipal().toPlainString());
        detalhes.put("total", baixa.getTotal().toPlainString());
        detalhes.put("paidOn", baixa.getDataPagamento().toString());
        detalhes.put("bankAccount", conta.getNome());
        auditoriaService.registrar(AcaoAuditoria.PAYMENT_REGISTERED, ENTIDADE, id, detalhes);
        return detalhe(lancamento);
    }

    @Transactional
    public LancamentoDetalhe estornarBaixa(UUID idCondominio, UUID idBaixa, String motivo) {
        acesso.exigir(idCondominio, Permissao.FINANCE_CANCEL);
        Baixa baixa = baixaRepository.buscarNoCondominio(idBaixa, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Baixa"));
        bloqueioPeriodo.exigirAberto(idCondominio, baixa.getDataPagamento());
        baixa.estornar(motivo.trim(), acesso.usuario().idUsuario(), Instant.now(clock));
        Lancamento lancamento = baixa.getLancamento();
        lancamento.estornarBaixa(baixa.getPrincipal(), acesso.usuario().idUsuario());
        lancamentoRepository.flush();
        auditoriaService.registrar(AcaoAuditoria.PAYMENT_REVERSED, ENTIDADE, lancamento.getId(), Map.of("paymentId", idBaixa.toString(),
                "total", baixa.getTotal().toPlainString(), "reason", motivo.trim()));
        return detalhe(lancamento);
    }

    // ---------------------------------------------------------------- apoio

    public Lancamento carregar(UUID idCondominio, UUID id) {
        return lancamentoRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Lançamento"));
    }

    private record Referencias(ContaPlano conta, CentroCusto centroCusto, Supplier fornecedor, Unidade unidade, Pessoa pessoa) {
    }

    private Referencias resolver(UUID idCondominio, LancamentoRequest r) {
        ContaPlano conta = configuracao.contaAnalitica(r.idContaPlano(), r.natureza() == Lancamento.Natureza.INCOME
                ? ContaPlano.Natureza.INCOME : ContaPlano.Natureza.EXPENSE);
        CentroCusto centroCusto = r.idCentroCusto() == null ? null : configuracao.carregarCentroCusto(idCondominio, r.idCentroCusto());
        Supplier fornecedor = r.idFornecedor() == null ? null
                : supplierRepository.findByIdAndOrganizationId(r.idFornecedor(), acesso.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
        Unidade unidade = r.idUnidade() == null ? null : unidadeRepository.findByIdAndIdCondominio(r.idUnidade(), idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        Pessoa pessoa = r.idPessoa() == null ? null
                : pessoaRepository.findByIdAndIdAdministradora(r.idPessoa(), acesso.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Pessoa"));
        if (r.natureza() == Lancamento.Natureza.INCOME && fornecedor != null) {
            throw ApiException.regraNegocio("SUPPLIER_ON_INCOME", "Receitas não são vinculadas a fornecedor.");
        }
        return new Referencias(conta, centroCusto, fornecedor, unidade, pessoa);
    }

    private void exigirDocumentoUnico(UUID idCondominio, Lancamento lancamento) {
        if (lancamento.getFornecedor() != null && lancamento.getNumeroDocumento() != null
                && lancamentoRepository.existeDocumentoFornecedor(idCondominio, lancamento.getFornecedor().getId(),
                lancamento.getNumeroDocumento(), lancamento.getId())) {
            throw ApiException.conflito("DUPLICATE_DOCUMENT",
                    "Já existe um lançamento deste fornecedor com o documento nº " + lancamento.getNumeroDocumento() + ".");
        }
    }

    public LancamentoResumo resumo(Lancamento e, LocalDate hoje) {
        String parcela = e.getParcela() == null ? null : e.getParcela() + "/" + e.getTotalParcelas();
        return new LancamentoResumo(e.getId(), e.getNatureza(), e.getDescricao(), e.getContaPlano().getCodigo(),
                e.getContaPlano().getNome(), e.getFornecedor() == null ? null : e.getFornecedor().getName(),
                e.getUnidade() == null ? null : e.getUnidade().rotulo(), e.getPessoa() == null ? null : e.getPessoa().getNome(),
                e.getValor(), e.getValorPago(), e.valorAberto(), e.getDataVencimento(), e.getCompetencia(), e.getSituacao(),
                e.getAprovacao(), e.isVencido(hoje), parcela, e.getNumeroDocumento(), e.getNumeroCobranca());
    }

    private LancamentoDetalhe detalhe(Lancamento e) {
        List<BaixaResponse> baixas = baixaRepository.buscarPorLancamento(e.getId()).stream().map(BaixaResponse::de).toList();
        return new LancamentoDetalhe(resumo(e, LocalDate.now(clock)), e.getContaPlano().getId(),
                e.getCentroCusto() == null ? null : e.getCentroCusto().getId(),
                e.getCentroCusto() == null ? null : e.getCentroCusto().getNome(),
                e.getFornecedor() == null ? null : e.getFornecedor().getId(), e.getUnidade() == null ? null : e.getUnidade().getId(),
                e.getPessoa() == null ? null : e.getPessoa().getId(), e.getObservacoes(), e.getOrigem(), e.getObservacaoAprovacao(),
                e.getAprovadoEm(), e.getMotivoCancelamento(), e.getCanceladoEm(), e.getPercentualMulta(),
                e.getPercentualJurosMensal(), e.getVersao(), baixas);
    }

    /** Chaves dos detalhes de auditoria em inglês: dado histórico gravado. */
    private static Map<String, Object> detalhesAuditoria(Lancamento e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", e.getNatureza().name());
        m.put("description", e.getDescricao());
        m.put("amount", e.getValor().toPlainString());
        m.put("dueDate", e.getDataVencimento().toString());
        m.put("account", e.getContaPlano().getCodigo());
        if (e.getNumeroDocumento() != null) m.put("document", e.getNumeroDocumento());
        return m;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
