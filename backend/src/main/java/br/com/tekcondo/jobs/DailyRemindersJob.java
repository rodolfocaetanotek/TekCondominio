package br.com.tekcondo.jobs;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.tekcondo.announcement.AnnouncementRepository;
import br.com.tekcondo.finance.entity.FinancialEntry;
import br.com.tekcondo.finance.repository.FinancialEntryRepository;
import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.notification.NotificationService;
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
    private final FinancialEntryRepository entries;
    private final AnnouncementRepository announcements;
    private final NotificationService notifications;
    private final Clock clock;

    public DailyRemindersJob(ContractRepository contracts, FinancialEntryRepository entries,
                             AnnouncementRepository announcements, NotificationService notifications, Clock clock) {
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
                sent += notifications.notify(c.getOrganizationId(),
                        notifications.usersWithPermission(c.getOrganizationId(), c.getCondominiumId(), Permission.SUPPLIER_VIEW),
                        (c.isExpired(today) ? "Contrato vencido: " : "Contrato a vencer: ") + c.getTitle(),
                        c.getSupplier().getName() + " — término em " + BR.format(c.getEndDate()),
                        "/contratos", "contract-expiring:" + c.getId() + ":" + c.getEndDate());
            }
        }
        return sent;
    }

    int payablesAwaitingApproval(LocalDate today) {
        Specification<FinancialEntry> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("kind"), FinancialEntry.Kind.EXPENSE),
                cb.equal(root.get("approval"), FinancialEntry.Approval.PENDING),
                cb.equal(root.get("status"), FinancialEntry.Status.OPEN),
                cb.lessThanOrEqualTo(root.get("dueDate"), today.plusDays(DAYS_AHEAD)));
        int sent = 0;
        for (FinancialEntry e : entries.findAll(spec)) {
            sent += notifications.notify(e.getOrganizationId(),
                    notifications.usersWithPermission(e.getOrganizationId(), e.getCondominiumId(), Permission.FINANCE_APPROVE),
                    "Pagamento aguardando aprovação", e.getDescription() + " — R$ " + e.getAmount().toPlainString()
                            + ", vence em " + BR.format(e.getDueDate()), "/financeiro/lancamentos/" + e.getId(),
                    "payable-approval:" + e.getId());
        }
        return sent;
    }

    int chargesDueSoon(LocalDate today) {
        Specification<FinancialEntry> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("kind"), FinancialEntry.Kind.INCOME),
                cb.isNotNull(root.get("unit")),
                root.get("status").in(FinancialEntry.Status.OPEN, FinancialEntry.Status.PARTIAL),
                cb.between(root.get("dueDate"), today, today.plusDays(DAYS_AHEAD)));
        int sent = 0;
        for (FinancialEntry e : entries.findAll(spec)) {
            List<java.util.UUID> residents = announcements.residentUsers(e.getCondominiumId(), "UNIT", null, e.getUnit().getId());
            sent += notifications.notify(e.getOrganizationId(), residents, "Cobrança a vencer",
                    e.getDescription() + " — R$ " + e.remaining().toPlainString() + ", vencimento " + BR.format(e.getDueDate()),
                    "/portal", "charge-due:" + e.getId());
        }
        return sent;
    }
}
