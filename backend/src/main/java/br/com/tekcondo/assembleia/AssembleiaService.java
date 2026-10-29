package br.com.tekcondo.assembleia;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.tekcondo.comunicado.Comunicado;
import br.com.tekcondo.comunicado.ComunicadoRepository;
import br.com.tekcondo.comunicado.ComunicadoService;
import br.com.tekcondo.assembleia.AssembleiaEntities.ItemPauta;
import br.com.tekcondo.assembleia.AssembleiaEntities.Assembleia;
import br.com.tekcondo.assembleia.AssembleiaEntities.Presenca;
import br.com.tekcondo.assembleia.AssembleiaEntities.Voto;
import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.estrutura.entity.Unidade;
import br.com.tekcondo.estrutura.repository.UnidadeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembleias: pauta, convocação, presença e procurações, votação por unidade (peso 1 ou fração ideal),
 * deliberações com responsável e prazo, e ata. Votos são registrados pela mesa (assembleia presencial ou híbrida),
 * um por unidade presente em cada item, sem alteração posterior.
 */
@Service
public class AssembleiaService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(FUSO);

    public record ItemPautaRequest(@NotBlank(message = "Informe o item da pauta.") @Size(max = 200) String titulo,
                                @Size(max = 2000) String descricao, boolean deliberativo) {
    }

    public record AssembleiaRequest(@NotBlank(message = "Informe o título.") @Size(max = 200) String titulo,
                          @NotNull(message = "Informe o tipo.") Assembleia.Tipo tipo,
                          @NotNull(message = "Informe a data e hora.") Instant primeiraConvocacaoEm,
                          Instant segundaConvocacaoEm,
                          @NotBlank(message = "Informe o local.") @Size(max = 255) String local,
                          @NotNull Assembleia.CriterioVoto criterioVoto,
                          @NotEmpty(message = "Inclua ao menos um item na pauta.") List<@Valid ItemPautaRequest> pauta) {
    }

    public record PresencaRequest(@NotNull(message = "Informe a unidade.") UUID idUnidade,
                                    @NotBlank(message = "Informe quem está presente.") @Size(max = 150) String nomePresente,
                                    boolean porProcuracao, @Size(max = 150) String nomeProcurador) {
    }

    public record VotoRequest(@NotNull UUID idItemPauta, @NotNull UUID idUnidade, @NotNull Voto.Escolha escolha) {
    }

    public record DeliberacaoRequest(@Size(max = 2000) String deliberacao, @Size(max = 150) String responsavel, LocalDate prazo) {
    }

    public record AtaRequest(@NotBlank(message = "Escreva a ata.") @Size(max = 100000) String ata) {
    }

    public record Apuracao(BigDecimal sim, BigDecimal nao, BigDecimal abstencao, long votos) {
    }

    public record ItemPautaResponse(UUID id, int posicao, String titulo, String descricao, boolean deliberativo, String deliberacao,
                               String responsavel, LocalDate prazo, Apuracao apuracao) {
    }

    public record PresencaResponse(UUID id, UUID idUnidade, String rotuloUnidade, String nomePresente, boolean porProcuracao,
                                     String nomeProcurador, Instant registradoEm) {
    }

    public record Quorum(long unidadesPresentes, long totalUnidades, BigDecimal fracaoPresente) {
    }

    public record Resumo(UUID id, String titulo, Assembleia.Tipo tipo, Instant primeiraConvocacaoEm, String local, Assembleia.Situacao situacao) {
    }

    public record Detalhe(UUID id, String titulo, Assembleia.Tipo tipo, Instant primeiraConvocacaoEm, Instant segundaConvocacaoEm, String local,
                         Assembleia.CriterioVoto criterioVoto, Assembleia.Situacao situacao, Instant convocadaEm, Instant encerradaEm,
                         String motivoCancelamento, String ata, Long versao, List<ItemPautaResponse> pauta,
                         List<PresencaResponse> presencas, Quorum quorum) {
    }

    private final AssembleiaRepository assembleias;
    private final ItemPautaRepository itensPauta;
    private final PresencaRepository listaPresenca;
    private final VotoRepository votos;
    private final UnidadeRepository unidades;
    private final ComunicadoRepository comunicados;
    private final ComunicadoService comunicadoService;
    private final AcessoCondominio acesso;
    private final AuditoriaService auditoria;
    private final Clock clock;

    public AssembleiaService(AssembleiaRepository assembleias, ItemPautaRepository itensPauta, PresencaRepository listaPresenca,
                           VotoRepository votos, UnidadeRepository unidades, ComunicadoRepository comunicados,
                           ComunicadoService comunicadoService, AcessoCondominio acesso, AuditoriaService auditoria,
                           Clock clock) {
        this.assembleias = assembleias;
        this.itensPauta = itensPauta;
        this.listaPresenca = listaPresenca;
        this.votos = votos;
        this.unidades = unidades;
        this.comunicados = comunicados;
        this.comunicadoService = comunicadoService;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(UUID idCondominio) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_VIEW);
        return assembleias.findByIdCondominioOrderByPrimeiraConvocacaoEmDesc(idCondominio).stream()
                .map(a -> new Resumo(a.getId(), a.getTitulo(), a.getTipo(), a.getPrimeiraConvocacaoEm(), a.getLocal(), a.getSituacao()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Detalhe buscar(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_VIEW);
        return detalhe(carregar(idCondominio, id));
    }

    @Transactional
    public Detalhe cadastrar(UUID idCondominio, AssembleiaRequest r) {
        Condominio c = acesso.exigirAtivo(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = new Assembleia(c.getIdAdministradora(), idCondominio, acesso.usuario().idUsuario());
        a.atualizar(r.titulo().trim(), r.tipo(), r.primeiraConvocacaoEm(), r.segundaConvocacaoEm(), r.local().trim(), r.criterioVoto());
        assembleias.save(a);
        salvarPauta(a, r.pauta());
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_CREATED, "Assembly", a.getId(), Map.of("title", a.getTitulo()));
        return detalhe(a);
    }

    @Transactional
    public Detalhe atualizar(UUID idCondominio, UUID id, AssembleiaRequest r) {
        acesso.exigirAtivo(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.atualizar(r.titulo().trim(), r.tipo(), r.primeiraConvocacaoEm(), r.segundaConvocacaoEm(), r.local().trim(), r.criterioVoto());
        itensPauta.deleteByIdAssembleia(a.getId());
        itensPauta.flush();
        salvarPauta(a, r.pauta());
        assembleias.flush();
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_UPDATED, "Assembly", id, Map.of("title", a.getTitulo()));
        return detalhe(a);
    }

    /** Convoca: publica comunicado com data, local e pauta para todos os condôminos (notificação + e-mail). */
    @Transactional
    public Detalhe convocar(UUID idCondominio, UUID id) {
        Condominio c = acesso.exigirAtivo(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        if (!a.getPrimeiraConvocacaoEm().isAfter(Instant.now(clock))) {
            throw ApiException.regraNegocio("ASSEMBLY_IN_PAST", "A data da assembleia já passou. Ajuste antes de convocar.");
        }
        a.convocar(Instant.now(clock));
        StringBuilder texto = new StringBuilder();
        texto.append("Ficam os senhores condôminos convocados para a Assembleia Geral ")
                .append(a.getTipo() == Assembleia.Tipo.ORDINARY ? "Ordinária" : "Extraordinária")
                .append(", a realizar-se em ").append(DATA_HORA.format(a.getPrimeiraConvocacaoEm()))
                .append(a.getSegundaConvocacaoEm() == null ? "" : " em primeira convocação, ou " + DATA_HORA.format(a.getSegundaConvocacaoEm())
                        + " em segunda convocação")
                .append(", no local: ").append(a.getLocal()).append(".\n\nOrdem do dia:\n");
        for (ItemPauta item : itensPauta.findByIdAssembleiaOrderByPosicaoAsc(a.getId())) {
            texto.append(item.getPosicao()).append(". ").append(item.getTitulo()).append('\n');
        }
        texto.append("\nO condômino poderá ser representado por procurador, mediante procuração.");
        var usuario = acesso.usuario();
        Comunicado comunicado = comunicados.save(new Comunicado(c.getIdAdministradora(), idCondominio,
                "Convocação: " + a.getTitulo(), texto.toString(), Comunicado.PublicoAlvo.ALL, null, null, true, true,
                a.getPrimeiraConvocacaoEm().atZone(FUSO).toLocalDate(), Instant.now(clock), usuario.idUsuario(), usuario.nome()));
        comunicadoService.distribuir(c, comunicado);
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CALLED"));
        return detalhe(a);
    }

    @Transactional
    public Detalhe iniciar(UUID idCondominio, UUID id) {
        acesso.exigirAtivo(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.iniciar();
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "IN_PROGRESS"));
        return detalhe(a);
    }

    @Transactional
    public Detalhe encerrar(UUID idCondominio, UUID id) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.encerrar(Instant.now(clock));
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CLOSED"));
        return detalhe(a);
    }

    @Transactional
    public Detalhe cancelar(UUID idCondominio, UUID id, String motivo) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.cancelar(motivo.trim());
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_STATUS_CHANGED, "Assembly", id, Map.of("status", "CANCELLED", "reason", motivo.trim()));
        return detalhe(a);
    }

    @Transactional
    public Detalhe registrarPresenca(UUID idCondominio, UUID id, PresencaRequest r) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.exigirEmAndamento();
        Unidade unidade = unidades.findByIdAndIdCondominio(r.idUnidade(), idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        if (listaPresenca.findByIdAssembleiaAndIdUnidade(id, unidade.getId()).isPresent()) {
            throw ApiException.conflito("ALREADY_PRESENT", "A unidade " + unidade.rotulo() + " já está na lista de presença.");
        }
        listaPresenca.save(new Presenca(id, unidade.getId(), r.nomePresente().trim(), r.porProcuracao(),
                r.nomeProcurador() == null ? null : r.nomeProcurador().trim(), acesso.usuario().idUsuario()));
        return detalhe(a);
    }

    @Transactional
    public Detalhe removerPresenca(UUID idCondominio, UUID id, UUID idPresenca) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.exigirEmAndamento();
        Presenca presenca = listaPresenca.findById(idPresenca).filter(x -> x.getIdAssembleia().equals(id))
                .orElseThrow(() -> ApiException.naoEncontrado("Presença"));
        if (votos.existsByIdAssembleiaAndIdUnidade(id, presenca.getIdUnidade())) {
            throw ApiException.regraNegocio("ATTENDANCE_HAS_VOTES", "A unidade já votou e não pode ser removida da presença.");
        }
        listaPresenca.delete(presenca);
        return detalhe(a);
    }

    @Transactional
    public Detalhe votar(UUID idCondominio, UUID id, VotoRequest r) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.exigirEmAndamento();
        ItemPauta item = itensPauta.findById(r.idItemPauta()).filter(i -> i.getIdAssembleia().equals(id))
                .orElseThrow(() -> ApiException.naoEncontrado("Item da pauta"));
        if (!item.isDeliberativo()) {
            throw ApiException.regraNegocio("ITEM_NOT_VOTING", "Este item da pauta não é deliberativo.");
        }
        Unidade unidade = unidades.findByIdAndIdCondominio(r.idUnidade(), idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Unidade"));
        if (listaPresenca.findByIdAssembleiaAndIdUnidade(id, unidade.getId()).isEmpty()) {
            throw ApiException.regraNegocio("UNIT_NOT_PRESENT", "Registre a presença da unidade antes do voto.");
        }
        if (votos.existsByIdItemPautaAndIdUnidade(item.getId(), unidade.getId())) {
            throw ApiException.conflito("ALREADY_VOTED", "A unidade " + unidade.rotulo() + " já votou neste item.");
        }
        BigDecimal peso = a.getCriterioVoto() == Assembleia.CriterioVoto.IDEAL_FRACTION ? unidade.getFracaoIdeal() : BigDecimal.ONE;
        votos.save(new Voto(id, item.getId(), unidade.getId(), r.escolha(), peso, acesso.usuario().idUsuario()));
        auditoria.registrar(AcaoAuditoria.VOTE_CAST, "Assembly", id, Map.of("item", item.getPosicao(), "unit", unidade.rotulo(),
                "choice", r.escolha().name()));
        return detalhe(a);
    }

    @Transactional
    public Detalhe deliberar(UUID idCondominio, UUID id, UUID idItemPauta, DeliberacaoRequest r) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        if (a.getSituacao() != Assembleia.Situacao.IN_PROGRESS && a.getSituacao() != Assembleia.Situacao.CLOSED) {
            throw ApiException.regraNegocio("ASSEMBLY_NOT_HELD", "Deliberações são registradas durante ou após a assembleia.");
        }
        ItemPauta item = itensPauta.findById(idItemPauta).filter(i -> i.getIdAssembleia().equals(id))
                .orElseThrow(() -> ApiException.naoEncontrado("Item da pauta"));
        item.deliberar(aparadoOuNulo(r.deliberacao()), aparadoOuNulo(r.responsavel()), r.prazo());
        return detalhe(a);
    }

    @Transactional
    public Detalhe registrarAta(UUID idCondominio, UUID id, String ata) {
        acesso.exigir(idCondominio, Permissao.ASSEMBLY_EDIT);
        Assembleia a = carregar(idCondominio, id);
        a.registrarAta(ata.trim());
        auditoria.registrar(AcaoAuditoria.ASSEMBLY_UPDATED, "Assembly", id, Map.of("minutes", "atualizada"));
        return detalhe(a);
    }

    /** Rascunho da ata gerado a partir da pauta, presença, resultados e deliberações registrados. */
    @Transactional(readOnly = true)
    public String rascunhoAta(UUID idCondominio, UUID id) {
        Condominio c = acesso.exigir(idCondominio, Permissao.ASSEMBLY_VIEW);
        Detalhe d = detalhe(carregar(idCondominio, id));
        StringBuilder sb = new StringBuilder();
        sb.append("ATA DA ASSEMBLEIA GERAL ").append(d.tipo() == Assembleia.Tipo.ORDINARY ? "ORDINÁRIA" : "EXTRAORDINÁRIA")
                .append(" DO ").append(c.getNome().toUpperCase()).append("\n\n");
        sb.append("Aos ").append(DATA_HORA.format(d.primeiraConvocacaoEm())).append(", em ").append(d.local())
                .append(", reuniram-se os condôminos, conforme lista de presença, representando ")
                .append(d.quorum().unidadesPresentes()).append(" de ").append(d.quorum().totalUnidades()).append(" unidades (")
                .append(d.quorum().fracaoPresente().stripTrailingZeros().toPlainString()).append("% das frações ideais).\n\n");
        sb.append("Critério de votação: ").append(d.criterioVoto() == Assembleia.CriterioVoto.UNIT ? "um voto por unidade" : "fração ideal")
                .append(".\n\nORDEM DO DIA E DELIBERAÇÕES\n");
        for (ItemPautaResponse item : d.pauta()) {
            sb.append('\n').append(item.posicao()).append(". ").append(item.titulo()).append('\n');
            if (item.deliberativo() && item.apuracao().votos() > 0) {
                sb.append("   Votação: favoráveis ").append(formatar(item.apuracao().sim())).append(", contrários ").append(formatar(item.apuracao().nao()))
                        .append(", abstenções ").append(formatar(item.apuracao().abstencao())).append(".\n");
            }
            if (item.deliberacao() != null) {
                sb.append("   Deliberação: ").append(item.deliberacao()).append('\n');
            }
            if (item.responsavel() != null) {
                sb.append("   Responsável: ").append(item.responsavel())
                        .append(item.prazo() == null ? "" : " — prazo: " + item.prazo().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        .append('\n');
            }
        }
        sb.append("\nNada mais havendo a tratar, encerrou-se a assembleia, lavrando-se a presente ata.\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------

    private void salvarPauta(Assembleia a, List<ItemPautaRequest> pauta) {
        for (int i = 0; i < pauta.size(); i++) {
            ItemPautaRequest it = pauta.get(i);
            itensPauta.save(new ItemPauta(a.getId(), i + 1, it.titulo().trim(), aparadoOuNulo(it.descricao()), it.deliberativo()));
        }
    }

    private Assembleia carregar(UUID idCondominio, UUID id) {
        return assembleias.findByIdAndIdCondominio(id, idCondominio).orElseThrow(() -> ApiException.naoEncontrado("Assembleia"));
    }

    private Detalhe detalhe(Assembleia a) {
        List<Voto> todosVotos = votos.findByIdAssembleia(a.getId());
        Map<UUID, List<Voto>> porItem = todosVotos.stream().collect(Collectors.groupingBy(Voto::getIdItemPauta));
        List<ItemPautaResponse> pauta = itensPauta.findByIdAssembleiaOrderByPosicaoAsc(a.getId()).stream().map(i -> {
            Map<Voto.Escolha, BigDecimal> somas = new EnumMap<>(Voto.Escolha.class);
            List<Voto> votosItem = porItem.getOrDefault(i.getId(), List.of());
            votosItem.forEach(v -> somas.merge(v.getEscolha(), v.getPeso(), BigDecimal::add));
            return new ItemPautaResponse(i.getId(), i.getPosicao(), i.getTitulo(), i.getDescricao(), i.isDeliberativo(), i.getDeliberacao(),
                    i.getResponsavel(), i.getPrazo(), new Apuracao(somas.getOrDefault(Voto.Escolha.YES, BigDecimal.ZERO),
                    somas.getOrDefault(Voto.Escolha.NO, BigDecimal.ZERO), somas.getOrDefault(Voto.Escolha.ABSTAIN, BigDecimal.ZERO), votosItem.size()));
        }).toList();
        List<Unidade> unidadesCondominio = unidades.buscarAtivasDoCondominio(a.getIdCondominio());
        Map<UUID, Unidade> unidadesPorId = unidadesCondominio.stream().collect(Collectors.toMap(Unidade::getId, Function.identity()));
        List<Presenca> presentes = listaPresenca.findByIdAssembleiaOrderByRegistradoEmAsc(a.getId());
        List<PresencaResponse> presencas = presentes.stream().map(x -> new PresencaResponse(x.getId(), x.getIdUnidade(),
                unidadesPorId.containsKey(x.getIdUnidade()) ? unidadesPorId.get(x.getIdUnidade()).rotulo() : unidades.findById(x.getIdUnidade()).map(Unidade::rotulo).orElse("?"),
                x.getNomePresente(), x.isPorProcuracao(), x.getNomeProcurador(), x.getRegistradoEm())).toList();
        BigDecimal fracao = presentes.stream().map(x -> unidadesPorId.get(x.getIdUnidade())).filter(u -> u != null)
                .map(Unidade::getFracaoIdeal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Detalhe(a.getId(), a.getTitulo(), a.getTipo(), a.getPrimeiraConvocacaoEm(), a.getSegundaConvocacaoEm(), a.getLocal(),
                a.getCriterioVoto(), a.getSituacao(), a.getConvocadaEm(), a.getEncerradaEm(), a.getMotivoCancelamento(), a.getAta(),
                a.getVersao(), pauta, presencas, new Quorum(presentes.size(), unidadesCondominio.size(), fracao));
    }

    private static String formatar(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String aparadoOuNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
