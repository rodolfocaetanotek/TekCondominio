package br.com.tekcondo.jobs;

import br.com.tekcondo.visitor.VisitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção LGPD: anonimiza diariamente os registros de portaria mais antigos que o prazo configurado. */
@Component
@ConditionalOnProperty(name = "tekcondo.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class PrivacyRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(PrivacyRetentionJob.class);

    private final VisitorService visitors;
    private final int visitorRetentionDays;

    public PrivacyRetentionJob(VisitorService visitors,
                               @Value("${tekcondo.privacy.visitor-retention-days:180}") int visitorRetentionDays) {
        this.visitors = visitors;
        this.visitorRetentionDays = visitorRetentionDays;
    }

    @Scheduled(cron = "${tekcondo.jobs.retention-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        int count = visitors.anonymizeOlderThan(visitorRetentionDays);
        log.info("Retenção LGPD: {} registro(s) de portaria anonimizado(s) (prazo {} dias)", count, visitorRetentionDays);
    }
}
