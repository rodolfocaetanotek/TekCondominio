package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.entity.ContaBancaria;
import br.com.tekcondo.financeiro.entity.FechamentoPeriodo;
import br.com.tekcondo.financeiro.repository.ContaBancariaRepository;
import br.com.tekcondo.financeiro.repository.FechamentoPeriodoRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fechamento mensal: congela o período (até o último dia de um mês já encerrado), grava uma fotografia dos saldos
 * e totais para a prestação de contas e passa a bloquear alterações retroativas (ver {@link BloqueioPeriodo}).
 */
@Service
public class FechamentoPeriodoService {

    public record FechamentoRequest(@NotNull(message = "Informe o último dia do período a fechar.") LocalDate ate,
                                    @Size(max = 500) String observacoes) {
    }

    /** {@code ate} nulo reabre todo o histórico. */
    public record ReaberturaRequest(LocalDate ate,
                                    @NotBlank(message = "Informe a justificativa.") @Size(min = 5, max = 255,
                                            message = "A justificativa deve ter entre 5 e 255 caracteres.") String motivo) {
    }

    public record SaldoConta(UUID id, String nome, BigDecimal saldo, BigDecimal saldoConciliado) {
    }

    /** Conferência antes de fechar: pendências que costumam indicar erro (não impedem o fechamento). */
    public record Verificacao(LocalDate de, LocalDate ate, long movimentacoesNaoConciliadas, long aprovacoesPendentes,
                              BigDecimal receitas, BigDecimal despesas, List<SaldoConta> saldos) {
    }

    public record ItemHistorico(UUID id, FechamentoPeriodo.Acao acao, LocalDate fechadoAte, LocalDate fechadoAteAnterior,
                                String observacoes, Map<String, Object> fotografia, Instant criadoEm, String nomeCriadoPor) {
    }

    public record SituacaoFechamento(LocalDate fechadoAte, LocalDate sugestaoAte, List<ItemHistorico> historico) {
    }

    private final BloqueioPeriodo bloqueio;
    private final FechamentoPeriodoRepository fechamentoRepository;
    private final ContaBancariaRepository contaBancariaRepository;
    private final ConsultasFinanceiras consultas;
    private final NamedParameterJdbcTemplate jdbc;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public FechamentoPeriodoService(BloqueioPeriodo bloqueio, FechamentoPeriodoRepository fechamentoRepository,
                                    ContaBancariaRepository contaBancariaRepository, ConsultasFinanceiras consultas,
                                    NamedParameterJdbcTemplate jdbc, AcessoCondominio acesso, AuditoriaService auditoriaService,
                                    Clock clock) {
        this.bloqueio = bloqueio;
        this.fechamentoRepository = fechamentoRepository;
        this.contaBancariaRepository = contaBancariaRepository;
        this.consultas = consultas;
        this.jdbc = jdbc;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SituacaoFechamento situacao(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        LocalDate fechado = bloqueio.fechadoAte(idCondominio);
        LocalDate ultimoEncerrado = LocalDate.now(clock).withDayOfMonth(1).minusDays(1);
        LocalDate sugestao = fechado == null ? ultimoEncerrado : fimDoMes(fechado.plusDays(1));
        return new SituacaoFechamento(fechado, sugestao.isAfter(ultimoEncerrado) ? null : sugestao,
                fechamentoRepository.findByIdCondominioOrderByCriadoEmDesc(idCondominio).stream()
                        .map(FechamentoPeriodoService::toItem).toList());
    }

    @Transactional(readOnly = true)
    public Verificacao verificar(UUID idCondominio, LocalDate ate) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return montarVerificacao(idCondominio, bloqueio.fechadoAte(idCondominio), ate);
    }

    @Transactional
    public SituacaoFechamento fechar(UUID idCondominio, FechamentoRequest request) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_CLOSE);
        LocalDate ate = request.ate();
        if (!ate.equals(fimDoMes(ate))) {
            throw ApiException.requisicaoInvalida("CLOSING_NOT_MONTH_END", "O fechamento é mensal: informe o último dia do mês.");
        }
        if (!ate.isBefore(LocalDate.now(clock).withDayOfMonth(1))) {
            throw ApiException.regraNegocio("CLOSING_MONTH_NOT_ENDED", "Só é possível fechar meses já encerrados.");
        }
        LocalDate anterior = bloqueio.travarParaFechamento(idCondominio);
        if (anterior != null && !ate.isAfter(anterior)) {
            throw ApiException.conflito("PERIOD_ALREADY_CLOSED", "O período até " + anterior + " já está fechado.");
        }
        Verificacao verificacao = montarVerificacao(idCondominio, anterior, ate);
        // Chaves da fotografia em inglês: é JSON gravado e lido pelo histórico (dado persistido).
        Map<String, Object> fotografia = new LinkedHashMap<>();
        fotografia.put("from", verificacao.de().toString());
        fotografia.put("until", ate.toString());
        fotografia.put("income", verificacao.receitas().toPlainString());
        fotografia.put("expense", verificacao.despesas().toPlainString());
        fotografia.put("unreconciledMovements", verificacao.movimentacoesNaoConciliadas());
        fotografia.put("pendingApprovals", verificacao.aprovacoesPendentes());
        fotografia.put("balances", verificacao.saldos().stream().map(b -> Map.of("account", b.nome(),
                "balance", b.saldo().toPlainString(), "reconciled", b.saldoConciliado().toPlainString())).toList());
        var usuario = acesso.usuario();
        FechamentoPeriodo fechamento = fechamentoRepository.save(new FechamentoPeriodo(condominio.getIdAdministradora(), idCondominio,
                FechamentoPeriodo.Acao.CLOSE, ate, anterior, emBranco(request.observacoes()), fotografia, usuario.idUsuario(),
                usuario.nome()));
        bloqueio.definirFechadoAte(idCondominio, ate);
        auditoriaService.registrar(AcaoAuditoria.PERIOD_CLOSED, "Condominium", idCondominio, Map.of("until", ate.toString(),
                "previous", String.valueOf(anterior), "closingId", fechamento.getId().toString()));
        return situacao(idCondominio);
    }

    @Transactional
    public SituacaoFechamento reabrir(UUID idCondominio, ReaberturaRequest request) {
        Condominio condominio = acesso.exigir(idCondominio, Permissao.FINANCE_CLOSE);
        LocalDate atual = bloqueio.travarParaFechamento(idCondominio);
        if (atual == null) {
            throw ApiException.regraNegocio("NOTHING_CLOSED", "Não há período fechado para reabrir.");
        }
        LocalDate ate = request.ate();
        if (ate != null && (!ate.equals(fimDoMes(ate)) || !ate.isBefore(atual))) {
            throw ApiException.requisicaoInvalida("INVALID_REOPEN_DATE",
                    "Informe o último dia de um mês anterior ao fechamento atual (ou deixe em branco para reabrir tudo).");
        }
        var usuario = acesso.usuario();
        fechamentoRepository.save(new FechamentoPeriodo(condominio.getIdAdministradora(), idCondominio, FechamentoPeriodo.Acao.REOPEN,
                ate, atual, request.motivo().trim(), null, usuario.idUsuario(), usuario.nome()));
        bloqueio.definirFechadoAte(idCondominio, ate);
        auditoriaService.registrar(AcaoAuditoria.PERIOD_REOPENED, "Condominium", idCondominio, Map.of("from", atual.toString(),
                "to", String.valueOf(ate), "reason", request.motivo().trim()));
        return situacao(idCondominio);
    }

    // ------------------------------------------------------------------

    private Verificacao montarVerificacao(UUID idCondominio, LocalDate anterior, LocalDate ate) {
        LocalDate de = anterior == null ? primeiraMovimentacao(idCondominio, ate) : anterior.plusDays(1);
        MapSqlParameterSource p = new MapSqlParameterSource("c", idCondominio).addValue("from", Date.valueOf(de))
                .addValue("until", Date.valueOf(ate));
        Long naoConciliadas = jdbc.queryForObject("""
                select (select count(*) from entry_payment where condominium_id = :c and reversed_at is null
                          and reconciled_at is null and paid_on <= :until)
                     + (select count(*) from bank_transfer where condominium_id = :c and reversed_at is null
                          and reconciled_at is null and transfer_date <= :until)
                """, p, Long.class);
        Long pendentes = jdbc.queryForObject("""
                select count(*) from financial_entry where condominium_id = :c and approval = 'PENDING'
                   and status in ('OPEN', 'PARTIAL') and due_date <= :until
                """, p, Long.class);
        BigDecimal[] totais = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("""
                select e.kind, coalesce(sum(pay.total), 0) as total
                  from entry_payment pay join financial_entry e on e.id = pay.entry_id
                 where pay.condominium_id = :c and pay.reversed_at is null and pay.paid_on between :from and :until
                 group by e.kind
                """, p, rs -> {
            totais["INCOME".equals(rs.getString("kind")) ? 0 : 1] = rs.getBigDecimal("total");
        });
        Map<UUID, BigDecimal> saldos = consultas.saldos(idCondominio, ate);
        List<SaldoConta> lista = new ArrayList<>();
        for (ContaBancaria b : contaBancariaRepository.findByIdCondominioOrderByNomeAsc(idCondominio)) {
            lista.add(new SaldoConta(b.getId(), b.getNome(), saldos.getOrDefault(b.getId(), b.getSaldoInicial()),
                    consultas.saldoConciliado(b.getId(), b.getSaldoInicial(), ate)));
        }
        return new Verificacao(de, ate, naoConciliadas == null ? 0 : naoConciliadas, pendentes == null ? 0 : pendentes,
                totais[0], totais[1], lista);
    }

    private LocalDate primeiraMovimentacao(UUID idCondominio, LocalDate padrao) {
        LocalDate primeira = jdbc.queryForObject("""
                select least((select min(paid_on) from entry_payment where condominium_id = :c),
                             (select min(transfer_date) from bank_transfer where condominium_id = :c),
                             (select min(opening_date) from bank_account where condominium_id = :c))
                """, new MapSqlParameterSource("c", idCondominio), LocalDate.class);
        return primeira == null || primeira.isAfter(padrao) ? padrao.withDayOfMonth(1) : primeira;
    }

    private static ItemHistorico toItem(FechamentoPeriodo f) {
        return new ItemHistorico(f.getId(), f.getAcao(), f.getFechadoAte(), f.getFechadoAteAnterior(), f.getObservacoes(),
                f.getFotografia(), f.getCriadoEm(), f.getNomeCriadoPor());
    }

    private static LocalDate fimDoMes(LocalDate d) {
        return d.withDayOfMonth(d.lengthOfMonth());
    }

    private static String emBranco(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
