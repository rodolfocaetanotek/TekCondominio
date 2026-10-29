package br.com.tekcondo.rotina;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.tekcondo.comunicado.ComunicadoRepository;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.fornecedor.entity.Contrato;
import br.com.tekcondo.fornecedor.repository.ContratoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lembretes diários (notificações internas, sem duplicidade graças à chave de deduplicação):
 * contratos a vencer, despesas aguardando aprovação com vencimento próximo e cobranças a vencer para os moradores.
 */
@Component
@ConditionalOnProperty(name = "tekcondo.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LembretesDiariosJob {

    private static final Logger log = LoggerFactory.getLogger(LembretesDiariosJob.class);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DIAS_ANTECEDENCIA = 3;

    private final ContratoRepository contratos;
    private final LancamentoRepository lancamentos;
    private final ComunicadoRepository comunicados;
    private final NotificacaoService notificacoes;
    private final Clock clock;

    public LembretesDiariosJob(ContratoRepository contratos, LancamentoRepository lancamentos,
                             ComunicadoRepository comunicados, NotificacaoService notificacoes, Clock clock) {
        this.contratos = contratos;
        this.lancamentos = lancamentos;
        this.comunicados = comunicados;
        this.notificacoes = notificacoes;
        this.clock = clock;
    }

    @Scheduled(cron = "${tekcondo.jobs.reminders-cron:0 0 7 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void executar() {
        LocalDate hoje = LocalDate.now(clock);
        int enviadas = contratosAVencer(hoje) + contasAguardandoAprovacao(hoje) + cobrancasAVencer(hoje);
        log.info("Lembretes diários: {} notificação(ões) enviada(s)", enviadas);
    }

    int contratosAVencer(LocalDate hoje) {
        int enviadas = 0;
        for (Contrato c : contratos.buscarAtivosComTermino()) {
            if (c.isAVencer(hoje) || c.isVencido(hoje)) {
                enviadas += notificacoes.notificar(c.getIdAdministradora(),
                        notificacoes.usuariosComPermissao(c.getIdAdministradora(), c.getIdCondominio(), Permissao.SUPPLIER_VIEW),
                        (c.isVencido(hoje) ? "Contrato vencido: " : "Contrato a vencer: ") + c.getTitulo(),
                        c.getFornecedor().getNome() + " — término em " + BR.format(c.getDataFim()),
                        "/contratos", "contract-expiring:" + c.getId() + ":" + c.getDataFim());
            }
        }
        return enviadas;
    }

    int contasAguardandoAprovacao(LocalDate hoje) {
        Specification<Lancamento> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("natureza"), Lancamento.Natureza.EXPENSE),
                cb.equal(root.get("aprovacao"), Lancamento.Aprovacao.PENDING),
                cb.equal(root.get("situacao"), Lancamento.Situacao.OPEN),
                cb.lessThanOrEqualTo(root.get("dataVencimento"), hoje.plusDays(DIAS_ANTECEDENCIA)));
        int enviadas = 0;
        for (Lancamento e : lancamentos.findAll(spec)) {
            enviadas += notificacoes.notificar(e.getIdAdministradora(),
                    notificacoes.usuariosComPermissao(e.getIdAdministradora(), e.getIdCondominio(), Permissao.FINANCE_APPROVE),
                    "Pagamento aguardando aprovação", e.getDescricao() + " — R$ " + e.getValor().toPlainString()
                            + ", vence em " + BR.format(e.getDataVencimento()), "/financeiro/lancamentos/" + e.getId(),
                    "payable-approval:" + e.getId());
        }
        return enviadas;
    }

    int cobrancasAVencer(LocalDate hoje) {
        Specification<Lancamento> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                cb.isNotNull(root.get("unidade")),
                root.get("situacao").in(Lancamento.Situacao.OPEN, Lancamento.Situacao.PARTIAL),
                cb.between(root.get("dataVencimento"), hoje, hoje.plusDays(DIAS_ANTECEDENCIA)));
        int enviadas = 0;
        for (Lancamento e : lancamentos.findAll(spec)) {
            List<java.util.UUID> moradores = comunicados.usuariosMoradores(e.getIdCondominio(), "UNIT", null, e.getUnidade().getId());
            enviadas += notificacoes.notificar(e.getIdAdministradora(), moradores, "Cobrança a vencer",
                    e.getDescricao() + " — R$ " + e.valorAberto().toPlainString() + ", vencimento " + BR.format(e.getDataVencimento()),
                    "/portal", "charge-due:" + e.getId());
        }
        return enviadas;
    }
}
