package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.CentroCustoResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaBancariaRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaBancariaResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaPlanoRequest;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaPlanoResponse;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ParametrosFinanceiros;
import br.com.tekcondo.financeiro.entity.CentroCusto;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.repository.CentroCustoRepository;
import br.com.tekcondo.financeiro.repository.ContaBancariaRepository;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Parâmetros financeiros do condomínio, plano de contas, centros de custo e contas bancárias. */
@Service
public class ConfiguracaoFinanceiraService {

    private final AcessoCondominio acesso;
    private final AcessoService acessoService;
    private final ContaPlanoRepository contaPlanoRepository;
    private final CentroCustoRepository centroCustoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final ConsultasFinanceiras consultas;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ConfiguracaoFinanceiraService(AcessoCondominio acesso, AcessoService acessoService,
                                         ContaPlanoRepository contaPlanoRepository, CentroCustoRepository centroCustoRepository,
                                         ContaBancariaRepository contaBancariaRepository, ConsultasFinanceiras consultas,
                                         AuditoriaService auditoriaService, Clock clock) {
        this.acesso = acesso;
        this.acessoService = acessoService;
        this.contaPlanoRepository = contaPlanoRepository;
        this.centroCustoRepository = centroCustoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.consultas = consultas;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- parâmetros

    @Transactional(readOnly = true)
    public ParametrosFinanceiros parametros(UUID idCondominio) {
        Condominio c = acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return new ParametrosFinanceiros(c.getPercentualMulta(), c.getPercentualJurosMensal(), c.getDiaVencimento(),
                c.isExigeAprovacaoContasPagar());
    }

    @Transactional
    public ParametrosFinanceiros atualizarParametros(UUID idCondominio, ParametrosFinanceiros request) {
        Condominio c = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        acessoService.exigir(Permissao.CONDOMINIUM_EDIT, idCondominio);
        ParametrosFinanceiros antes = new ParametrosFinanceiros(c.getPercentualMulta(), c.getPercentualJurosMensal(),
                c.getDiaVencimento(), c.isExigeAprovacaoContasPagar());
        c.atualizarParametrosFinanceiros(request.percentualMulta(), request.percentualJurosMensal(), request.diaVencimento(),
                request.exigeAprovacaoContasPagar(), acesso.usuario().idUsuario());
        auditoriaService.registrar(AcaoAuditoria.FINANCE_SETTINGS_CHANGED, "Condominium", idCondominio,
                Map.of("from", antes.toString(), "to", request.toString()));
        return request;
    }

    // ---------------------------------------------------------------- plano de contas

    @Transactional(readOnly = true)
    public List<ContaPlanoResponse> planoContas() {
        acessoService.exigirEscopo(Permissao.FINANCE_VIEW);
        return contaPlanoRepository.buscarDisponiveis(acesso.idAdministradora()).stream().map(ContaPlanoResponse::de).toList();
    }

    @Transactional
    public ContaPlanoResponse cadastrarContaPlano(ContaPlanoRequest request) {
        acessoService.exigirNaAdministradora(Permissao.FINANCE_EDIT);
        UUID idAdministradora = acesso.idAdministradora();
        String codigo = request.codigo().trim();
        if (contaPlanoRepository.existeCodigo(idAdministradora, codigo)) {
            throw ApiException.conflito("CHART_ACCOUNT_CODE_TAKEN", "Já existe uma conta com este código.");
        }
        ContaPlano conta = contaPlanoRepository.save(new ContaPlano(idAdministradora, codigo, request.nome(), request.natureza()));
        auditoriaService.registrar(AcaoAuditoria.CHART_ACCOUNT_CREATED, "ChartAccount", conta.getId(),
                Map.of("code", codigo, "name", conta.getNome()));
        return ContaPlanoResponse.de(conta);
    }

    @Transactional
    public ContaPlanoResponse atualizarContaPlano(UUID id, String nome, boolean ativo) {
        acessoService.exigirNaAdministradora(Permissao.FINANCE_EDIT);
        ContaPlano conta = contaPlanoRepository.buscarDisponivel(id, acesso.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Conta"));
        if (conta.isSistema()) {
            throw ApiException.regraNegocio("SYSTEM_ACCOUNT", "Contas do plano padrão não podem ser alteradas. Crie uma conta própria.");
        }
        conta.renomear(nome);
        conta.setAtivo(ativo);
        auditoriaService.registrar(AcaoAuditoria.CHART_ACCOUNT_UPDATED, "ChartAccount", id, Map.of("name", nome, "active", ativo));
        return ContaPlanoResponse.de(conta);
    }

    public ContaPlano contaAnalitica(UUID id, ContaPlano.Natureza esperada) {
        ContaPlano conta = contaPlanoRepository.buscarDisponivel(id, acesso.idAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Conta do plano de contas"));
        if (!conta.isAnalitica() || !conta.isAtivo()) {
            throw ApiException.regraNegocio("ACCOUNT_NOT_POSTABLE", "Escolha uma conta analítica ativa (não um grupo).");
        }
        if (esperada != null && conta.getNatureza() != esperada) {
            throw ApiException.regraNegocio("ACCOUNT_NATURE_MISMATCH", esperada == ContaPlano.Natureza.INCOME
                    ? "Receitas devem usar uma conta de receita." : "Despesas devem usar uma conta de despesa.");
        }
        return conta;
    }

    // ---------------------------------------------------------------- centros de custo

    @Transactional(readOnly = true)
    public List<CentroCustoResponse> centrosCusto(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return centroCustoRepository.findByIdCondominioOrderByNomeAsc(idCondominio).stream()
                .map(c -> new CentroCustoResponse(c.getId(), c.getNome(), c.isAtivo())).toList();
    }

    @Transactional
    public CentroCustoResponse salvarCentroCusto(UUID idCondominio, UUID id, String nome, Boolean ativo) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        centroCustoRepository.buscarPorNome(idCondominio, nome.trim()).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
            throw ApiException.conflito("COST_CENTER_NAME_TAKEN", "Já existe um centro de custo com este nome.");
        });
        CentroCusto centro = id == null ? new CentroCusto(condominio.getIdAdministradora(), idCondominio, nome)
                : centroCustoRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Centro de custo"));
        centro.renomear(nome);
        if (ativo != null) {
            centro.setAtivo(ativo);
        }
        centroCustoRepository.save(centro);
        auditoriaService.registrar(AcaoAuditoria.COST_CENTER_SAVED, "CostCenter", centro.getId(), Map.of("name", centro.getNome()));
        return new CentroCustoResponse(centro.getId(), centro.getNome(), centro.isAtivo());
    }

    // ---------------------------------------------------------------- contas bancárias

    @Transactional(readOnly = true)
    public List<ContaBancariaResponse> contasBancarias(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        Map<UUID, BigDecimal> saldos = consultas.saldos(idCondominio, LocalDate.now(clock));
        return contaBancariaRepository.findByIdCondominioOrderByNomeAsc(idCondominio).stream()
                .map(b -> toResponse(b, saldos.getOrDefault(b.getId(), b.getSaldoInicial()))).toList();
    }

    @Transactional
    public ContaBancariaResponse cadastrarContaBancaria(UUID idCondominio, ContaBancariaRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        exigirNomeContaLivre(idCondominio, request.nome(), null);
        ContaBancaria conta = new ContaBancaria(condominio.getIdAdministradora(), idCondominio, request.tipo(),
                request.saldoInicial() == null ? BigDecimal.ZERO : request.saldoInicial(),
                request.dataAbertura() == null ? LocalDate.now(clock) : request.dataAbertura());
        conta.atualizar(request.nome(), emBranco(request.codigoBanco()), emBranco(request.agencia()), emBranco(request.numeroConta()));
        contaBancariaRepository.save(conta);
        auditoriaService.registrar(AcaoAuditoria.BANK_ACCOUNT_SAVED, "BankAccount", conta.getId(), Map.of("name", conta.getNome(),
                "openingBalance", conta.getSaldoInicial().toPlainString()));
        return toResponse(conta, conta.getSaldoInicial());
    }

    @Transactional
    public ContaBancariaResponse atualizarContaBancaria(UUID idCondominio, UUID id, ContaBancariaRequest request, Boolean ativo) {
        acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        ContaBancaria conta = carregarContaBancaria(idCondominio, id);
        exigirNomeContaLivre(idCondominio, request.nome(), id);
        conta.atualizar(request.nome(), emBranco(request.codigoBanco()), emBranco(request.agencia()), emBranco(request.numeroConta()));
        if (ativo != null) {
            conta.setAtivo(ativo);
        }
        auditoriaService.registrar(AcaoAuditoria.BANK_ACCOUNT_SAVED, "BankAccount", id, Map.of("name", conta.getNome(),
                "active", conta.isAtivo()));
        return toResponse(conta, consultas.saldos(idCondominio, LocalDate.now(clock)).getOrDefault(id, conta.getSaldoInicial()));
    }

    public ContaBancaria carregarContaBancaria(UUID idCondominio, UUID id) {
        return contaBancariaRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Conta bancária"));
    }

    public CentroCusto carregarCentroCusto(UUID idCondominio, UUID id) {
        CentroCusto centro = centroCustoRepository.findByIdAndIdCondominio(id, idCondominio)
                .orElseThrow(() -> ApiException.naoEncontrado("Centro de custo"));
        if (!centro.isAtivo()) {
            throw ApiException.regraNegocio("COST_CENTER_INACTIVE", "O centro de custo está inativo.");
        }
        return centro;
    }

    private void exigirNomeContaLivre(UUID idCondominio, String nome, UUID excetoId) {
        contaBancariaRepository.buscarPorNome(idCondominio, nome.trim().toLowerCase(Locale.ROOT))
                .filter(b -> !b.getId().equals(excetoId))
                .ifPresent(b -> {
                    throw ApiException.conflito("BANK_ACCOUNT_NAME_TAKEN", "Já existe uma conta com este nome.");
                });
    }

    private static ContaBancariaResponse toResponse(ContaBancaria b, BigDecimal saldo) {
        return new ContaBancariaResponse(b.getId(), b.getNome(), b.getTipo(), b.getCodigoBanco(), b.getAgencia(),
                b.getNumeroConta(), b.getSaldoInicial(), b.getDataAbertura(), b.isAtivo(), saldo);
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
