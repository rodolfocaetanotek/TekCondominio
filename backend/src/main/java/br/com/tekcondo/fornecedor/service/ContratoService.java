package br.com.tekcondo.fornecedor.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.fornecedor.entity.Contrato;
import br.com.tekcondo.fornecedor.entity.HistoricoContrato;
import br.com.tekcondo.fornecedor.entity.Fornecedor;
import br.com.tekcondo.fornecedor.repository.HistoricoContratoRepository;
import br.com.tekcondo.fornecedor.repository.ContratoRepository;
import br.com.tekcondo.fornecedor.repository.FornecedorRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContratoService {

    public record ContratoRequest(@NotNull(message = "Informe o fornecedor.") UUID idFornecedor,
                                  @NotBlank(message = "Informe o objeto do contrato.") @Size(max = 200) String titulo,
                                  @Size(max = 2000) String descricao,
                                  @NotNull(message = "Informe o valor.") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal valor,
                                  @NotNull Contrato.Periodicidade periodicidade,
                                  @NotNull(message = "Informe o início da vigência.") LocalDate dataInicio,
                                  LocalDate dataFim,
                                  @Size(max = 20) String indiceReajuste,
                                  @Min(1) @Max(12) Integer mesReajuste,
                                  @Min(0) @Max(365) Integer diasAvisoPrevio,
                                  Long versao) {
    }

    public record EncerramentoRequest(@NotNull(message = "Informe a data de encerramento.") LocalDate dataEncerramento,
                                      @NotBlank(message = "Informe o motivo.") @Size(max = 255) String motivo) {
    }

    public record Historico(String alteracao, Instant criadoEm, String nomeCriadoPor) {
    }

    public record ContratoResponse(UUID id, UUID idFornecedor, String nomeFornecedor, String titulo, String descricao, BigDecimal valor,
                                   Contrato.Periodicidade periodicidade, LocalDate dataInicio, LocalDate dataFim, String indiceReajuste,
                                   Integer mesReajuste, int diasAvisoPrevio, Contrato.Situacao situacao, boolean aVencer, boolean vencido,
                                   LocalDate dataEncerramento, String motivoEncerramento, Long versao, List<Historico> historico) {
    }

    private final ContratoRepository contratoRepository;
    private final HistoricoContratoRepository historicoRepository;
    private final FornecedorRepository fornecedorRepository;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public ContratoService(ContratoRepository contratoRepository, HistoricoContratoRepository historicoRepository,
                           FornecedorRepository fornecedorRepository, AcessoCondominio acesso, AuditoriaService auditoria, Clock clock) {
        this.contratoRepository = contratoRepository;
        this.historicoRepository = historicoRepository;
        this.fornecedorRepository = fornecedorRepository;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ContratoResponse> listar(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.SUPPLIER_VIEW);
        return contratoRepository.buscarDoCondominio(idCondominio).stream().map(c -> paraResponse(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public ContratoResponse buscar(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.SUPPLIER_VIEW);
        return paraResponse(carregar(idCondominio, id), true);
    }

    @Transactional
    public ContratoResponse salvar(UUID idCondominio, UUID id, ContratoRequest r) {
        Condominio condominio = acesso.exigirAtivo(idCondominio, Permissao.SUPPLIER_EDIT);
        Fornecedor fornecedor = fornecedorRepository.findByIdAndIdAdministradora(r.idFornecedor(), condominio.getIdAdministradora())
                .orElseThrow(() -> ApiException.naoEncontrado("Fornecedor"));
        Contrato contrato;
        List<String> alteracoes = new ArrayList<>();
        if (id == null) {
            contrato = new Contrato(condominio.getIdAdministradora(), idCondominio);
            alteracoes.add("Contrato cadastrado");
        } else {
            contrato = carregar(idCondominio, id);
            if (r.versao() == null || !r.versao().equals(contrato.getVersao())) {
                throw ApiException.versaoObsoleta();
            }
            registrarDiferenca(alteracoes, "Fornecedor", contrato.getFornecedor().getNome(), fornecedor.getNome());
            registrarDiferenca(alteracoes, "Valor", contrato.getValor() == null ? null : contrato.getValor().toPlainString(), r.valor().toPlainString());
            registrarDiferenca(alteracoes, "Término", contrato.getDataFim(), r.dataFim());
            registrarDiferenca(alteracoes, "Início", contrato.getDataInicio(), r.dataInicio());
            registrarDiferenca(alteracoes, "Objeto", contrato.getTitulo(), r.titulo().trim());
        }
        contrato.atualizar(fornecedor, r.titulo().trim(), aparadoOuNulo(r.descricao()), r.valor(), r.periodicidade(), r.dataInicio(),
                r.dataFim(), aparadoOuNulo(r.indiceReajuste()), r.mesReajuste(), r.diasAvisoPrevio() == null ? 30 : r.diasAvisoPrevio());
        contratoRepository.save(contrato);
        contratoRepository.flush();
        if (!alteracoes.isEmpty()) {
            historicoRepository.save(new HistoricoContrato(contrato.getId(), String.join("; ", alteracoes), acesso.usuario().nome()));
        }
        auditoria.registrar(id == null ? AcaoAuditoria.CONTRACT_CREATED : AcaoAuditoria.CONTRACT_UPDATED, "Contract", contrato.getId(),
                Map.of("title", contrato.getTitulo(), "changes", String.join("; ", alteracoes)));
        return paraResponse(contrato, true);
    }

    @Transactional
    public ContratoResponse encerrar(UUID idCondominio, UUID id, EncerramentoRequest r) {
        acesso.exigir(idCondominio, Permissao.SUPPLIER_EDIT);
        Contrato contrato = carregar(idCondominio, id);
        contrato.encerrar(r.dataEncerramento(), r.motivo().trim());
        historicoRepository.save(new HistoricoContrato(contrato.getId(), "Encerrado em " + r.dataEncerramento() + ": " + r.motivo().trim(),
                acesso.usuario().nome()));
        auditoria.registrar(AcaoAuditoria.CONTRACT_TERMINATED, "Contract", id, Map.of("reason", r.motivo().trim()));
        return paraResponse(contrato, true);
    }

    private Contrato carregar(UUID idCondominio, UUID id) {
        return contratoRepository.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Contrato"));
    }

    private ContratoResponse paraResponse(Contrato c, boolean comHistorico) {
        LocalDate hoje = LocalDate.now(clock);
        List<Historico> h = comHistorico ? historicoRepository.findByIdContratoOrderByCriadoEmDesc(c.getId()).stream()
                .map(x -> new Historico(x.getAlteracao(), x.getCriadoEm(), x.getNomeCriadoPor())).toList() : List.of();
        return new ContratoResponse(c.getId(), c.getFornecedor().getId(), c.getFornecedor().getNome(), c.getTitulo(), c.getDescricao(),
                c.getValor(), c.getPeriodicidade(), c.getDataInicio(), c.getDataFim(), c.getIndiceReajuste(),
                c.getMesReajuste(), c.getDiasAvisoPrevio(), c.getSituacao(), c.isAVencer(hoje), c.isVencido(hoje),
                c.getDataEncerramento(), c.getMotivoEncerramento(), c.getVersao(), h);
    }

    private static void registrarDiferenca(List<String> alteracoes, String rotulo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            alteracoes.add(rotulo + ": " + (antes == null ? "—" : antes) + " → " + (depois == null ? "—" : depois));
        }
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
