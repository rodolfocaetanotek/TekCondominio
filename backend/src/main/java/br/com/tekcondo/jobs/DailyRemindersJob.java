package br.com.tekcondo.jobs;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.tekcondo.announcement.AnnouncementRepository;
import br.com.tekcondo.financeiro.entity.Lancamento;
import br.com.tekcondo.financeiro.repository.LancamentoRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.notificacao.NotificacaoService;
import br.com.tekcondo.supplier.entity.Contract;
import br.com.tekcondo.supplier.repository.ContractRepository;
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
public class DailyRemindersJob {

    private static final Logger log = LoggerFactory.getLogger(DailyRemindersJob.class);
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int DAYS_AHEAD = 3;

    private final ContractRepository contracts;
    private final LancamentoRepository entries;
    private final AnnouncementRepository announcements;
    private final NotificacaoService notifications;
    private final Clock clock;

    public DailyRemindersJob(ContractRepository contracts, LancamentoRepository entries,
                             AnnouncementRepository announcements, NotificacaoService notifications, Clock clock) {
        this.contracts = contracts;
        this.entries = entries;
        this.announcements = announcements;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${tekcondo.jobs.reminders-cron:0 0 7 * * *}", zone = "America/Sao_Paulo")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now(clock);
        int sent = contractsExpiring(today) + payablesAwaitingApproval(today) + chargesDueSoon(today);
        log.info("Lembretes diários: {} notificação(ões) enviada(s)", sent);
    }

    int contractsExpiring(LocalDate today) {
        int sent = 0;
        for (Contract c : contracts.findActiveWithEndDate()) {
            if (c.isExpiring(today) || c.isExpired(today)) {
                sent += notifications.notificar(c.getOrganizationId(),
                        notifications.usuariosComPermissao(c.getOrganizationId(), c.getCondominiumId(), Permissao.SUPPLIER_VIEW),
                        (c.isExpired(today) ? "Contrato vencido: " : "Contrato a vencer: ") + c.getTitle(),
                        c.getSupplier().getName() + " — término em " + BR.format(c.getEndDate()),
                        "/contratos", "contract-expiring:" + c.getId() + ":" + c.getEndDate());
            }
        }
        return sent;
    }

    int payablesAwaitingApproval(LocalDate today) {
        Specification<Lancamento> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("natureza"), Lancamento.Natureza.EXPENSE),
                cb.equal(root.get("aprovacao"), Lancamento.Aprovacao.PENDING),
                cb.equal(root.get("situacao"), Lancamento.Situacao.OPEN),
                cb.lessThanOrEqualTo(root.get("dataVencimento"), today.plusDays(DAYS_AHEAD)));
        int sent = 0;
        for (Lancamento e : entries.findAll(spec)) {
            sent += notifications.notificar(e.getIdAdministradora(),
                    notifications.usuariosComPermissao(e.getIdAdministradora(), e.getIdCondominio(), Permissao.FINANCE_APPROVE),
                    "Pagamento aguardando aprovação", e.getDescricao() + " — R$ " + e.getValor().toPlainString()
                            + ", vence em " + BR.format(e.getDataVencimento()), "/financeiro/lancamentos/" + e.getId(),
                    "payable-approval:" + e.getId());
        }
        return sent;
    }

    int chargesDueSoon(LocalDate today) {
        Specification<Lancamento> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("natureza"), Lancamento.Natureza.INCOME),
                cb.isNotNull(root.get("unidade")),
                root.get("situacao").in(Lancamento.Situacao.OPEN, Lancamento.Situacao.PARTIAL),
                cb.between(root.get("dataVencimento"), today, today.plusDays(DAYS_AHEAD)));
        int sent = 0;
        for (Lancamento e : entries.findAll(spec)) {
            List<java.util.UUID> residents = announcements.residentUsers(e.getIdCondominio(), "UNIT", null, e.getUnidade().getId());
            sent += notifications.notificar(e.getIdAdministradora(), residents, "Cobrança a vencer",
                    e.getDescricao() + " — R$ " + e.valorAberto().toPlainString() + ", vencimento " + BR.format(e.getDataVencimento()),
                    "/portal", "charge-due:" + e.getId());
        }
        return sent;
    }
}
