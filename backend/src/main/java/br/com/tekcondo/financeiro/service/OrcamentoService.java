package br.com.tekcondo.financeiro.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.CelulaOrcamento;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.ContaOrcamento;
import br.com.tekcondo.financeiro.dto.FinanceiroDtos.Orcamento;
import br.com.tekcondo.financeiro.entity.ContaPlano;
import br.com.tekcondo.financeiro.entity.LinhaOrcamento;
import br.com.tekcondo.financeiro.repository.ContaPlanoRepository;
import br.com.tekcondo.financeiro.repository.LinhaOrcamentoRepository;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orçamento anual por conta e mês, comparado com o realizado (regime de caixa). */
@Service
public class OrcamentoService {

    private final LinhaOrcamentoRepository linhaRepository;
    private final ContaPlanoRepository contaPlanoRepository;
    private final ConfiguracaoFinanceiraService configuracao;
    private final ConsultasFinanceiras consultas;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoriaService;

    public OrcamentoService(LinhaOrcamentoRepository linhaRepository, ContaPlanoRepository contaPlanoRepository,
                            ConfiguracaoFinanceiraService configuracao, ConsultasFinanceiras consultas, AcessoCondominio acesso,
                            AuditoriaService auditoriaService) {
        this.linhaRepository = linhaRepository;
        this.contaPlanoRepository = contaPlanoRepository;
        this.configuracao = configuracao;
        this.consultas = consultas;
        this.acesso = acesso;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public Orcamento orcamento(UUID idCondominio, int ano) {
        acesso.exigir(idCondominio, Permissao.FINANCE_VIEW);
        return montar(idCondominio, ano);
    }

    @Transactional
    public Orcamento salvar(UUID idCondominio, int ano, List<CelulaOrcamento> celulas) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.FINANCE_EDIT);
        Map<String, LinhaOrcamento> existentes = new HashMap<>();
        linhaRepository.findByIdCondominioAndAno(idCondominio, ano)
                .forEach(l -> existentes.put(l.getIdContaPlano() + ":" + l.getMes(), l));
        UUID por = acesso.usuario().idUsuario();
        for (CelulaOrcamento celula : celulas) {
            configuracao.contaAnalitica(celula.idContaPlano(), null);
            LinhaOrcamento linha = existentes.computeIfAbsent(celula.idContaPlano() + ":" + celula.mes(),
                    k -> new LinhaOrcamento(condominio.getIdAdministradora(), idCondominio, ano, celula.mes(), celula.idContaPlano()));
            linha.setValor(celula.valor(), por);
            linhaRepository.save(linha);
        }
        auditoriaService.registrar(AcaoAuditoria.BUDGET_UPDATED, "Condominium", idCondominio,
                Map.of("year", ano, "cells", celulas.size()));
        return montar(idCondominio, ano);
    }

    private Orcamento montar(UUID idCondominio, int ano) {
        Map<UUID, BigDecimal[]> previsto = new HashMap<>();
        for (LinhaOrcamento l : linhaRepository.findByIdCondominioAndAno(idCondominio, ano)) {
            previsto.computeIfAbsent(l.getIdContaPlano(), k -> zeros())[l.getMes() - 1] = l.getValor();
        }
        Map<UUID, BigDecimal[]> realizado = consultas.realizadoPorContaEMes(idCondominio, ano);
        List<ContaOrcamento> contas = contaPlanoRepository.buscarDisponiveis(acesso.idAdministradora()).stream()
                .filter(ContaPlano::isAnalitica)
                .filter(a -> a.isAtivo() || previsto.containsKey(a.getId()) || realizado.containsKey(a.getId()))
                .map(a -> {
                    BigDecimal[] p = previsto.getOrDefault(a.getId(), zeros());
                    BigDecimal[] r = realizado.getOrDefault(a.getId(), zeros());
                    return new ContaOrcamento(a.getId(), a.getCodigo(), a.getNome(), a.getNatureza(), Arrays.asList(p),
                            Arrays.asList(r), somar(p), somar(r));
                })
                .toList();
        return new Orcamento(ano, contas);
    }

    private static BigDecimal[] zeros() {
        BigDecimal[] m = new BigDecimal[12];
        Arrays.fill(m, BigDecimal.ZERO);
        return m;
    }

    private static BigDecimal somar(BigDecimal[] valores) {
        return Arrays.stream(valores).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
