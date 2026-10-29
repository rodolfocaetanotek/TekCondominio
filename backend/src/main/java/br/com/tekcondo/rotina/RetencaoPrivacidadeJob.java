package br.com.tekcondo.rotina;

import br.com.tekcondo.portaria.PortariaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retenção LGPD: anonimiza diariamente os registros de portaria mais antigos que o prazo configurado. */
@Component
@ConditionalOnProperty(name = "tekcondo.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class RetencaoPrivacidadeJob {

    private static final Logger log = LoggerFactory.getLogger(RetencaoPrivacidadeJob.class);

    private final PortariaService portariaService;
    private final int diasRetencaoVisitantes;

    public RetencaoPrivacidadeJob(PortariaService portariaService,
                               @Value("${tekcondo.privacy.visitor-retention-days:180}") int diasRetencaoVisitantes) {
        this.portariaService = portariaService;
        this.diasRetencaoVisitantes = diasRetencaoVisitantes;
    }

    @Scheduled(cron = "${tekcondo.jobs.retention-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void executar() {
        int quantidade = portariaService.anonimizarAnterioresA(diasRetencaoVisitantes);
        log.info("Retenção LGPD: {} registro(s) de portaria anonimizado(s) (prazo {} dias)", quantidade, diasRetencaoVisitantes);
    }
}
