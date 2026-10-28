package br.com.tekcondo.document.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.auditoria.service.AcaoAuditoria;
import br.com.tekcondo.auditoria.service.AuditoriaService;
import br.com.tekcondo.condominio.entity.Condominio;
import br.com.tekcondo.condominio.service.AcessoCondominio;
import br.com.tekcondo.document.entity.Document;
import br.com.tekcondo.document.repository.DocumentRepository;
import br.com.tekcondo.acesso.autorizacao.AcessoService;
import br.com.tekcondo.acesso.autorizacao.Permissao;
import br.com.tekcondo.integracao.armazenamento.ArmazenamentoArquivos;
import br.com.tekcondo.comum.config.TekCondoProperties;
import br.com.tekcondo.comum.exception.ApiException;
import br.com.tekcondo.comum.web.PaginaResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositório de documentos e anexos. Valida extensão E conteúdo real (assinatura do arquivo), limita tamanho
 * e só entrega arquivos por endpoint autenticado — nunca por URL pública.
 */
@Service
public class DocumentService {

    /** Extensões aceitas → tipo MIME servido. */
    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv", "text/csv", "txt", "text/plain");

    /** Anexos vinculados a registros de outros módulos e a permissão exigida (consulta, edição). */
    private static final Map<String, Permissao[]> ENTITY_PERMISSIONS = Map.of(
            "FinancialEntry", new Permissao[]{Permissao.FINANCE_VIEW, Permissao.FINANCE_EDIT},
            "Occurrence", new Permissao[]{Permissao.OCCURRENCE_VIEW, Permissao.OCCURRENCE_EDIT},
            "Contract", new Permissao[]{Permissao.SUPPLIER_VIEW, Permissao.SUPPLIER_EDIT},
            "Assembly", new Permissao[]{Permissao.ASSEMBLY_VIEW, Permissao.ASSEMBLY_EDIT});

    public record Upload(Document.Category category, String title, String description, Document.Visibility visibility,
                         String entityType, UUID entityId, UUID unitId, String originalName, byte[] content) {
    }

    public record DocumentResponse(UUID id, Document.Category category, String title, String description, String fileName,
                                   String contentType, long sizeBytes, Document.Visibility visibility, String entityType,
                                   UUID entityId, UUID unitId, boolean archived, Instant createdAt, String createdByName) {

        public static DocumentResponse from(Document d) {
            return new DocumentResponse(d.getId(), d.getCategory(), d.getTitle(), d.getDescription(), d.getFileName(),
                    d.getContentType(), d.getSizeBytes(), d.getVisibility(), d.getEntityType(), d.getEntityId(),
                    d.getUnitId(), d.getArchivedAt() != null, d.getCreatedAt(), d.getCreatedByName());
        }
    }

    public record FileContent(String fileName, String contentType, byte[] content) {
    }

    private final DocumentRepository repository;
    private final ArmazenamentoArquivos storage;
    private final AcessoCondominio access;
    private final AcessoService accessService;
    private final AuditoriaService auditService;
    private final TekCondoProperties props;
    private final Clock clock;

    public DocumentService(DocumentRepository repository, ArmazenamentoArquivos storage, AcessoCondominio access,
                           AcessoService accessService, AuditoriaService auditService, TekCondoProperties props, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.access = access;
        this.accessService = accessService;
        this.auditService = auditService;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PaginaResponse<DocumentResponse> list(UUID condominiumId, Document.Category category, String entityType, UUID entityId,
                                               String search, boolean includeArchived, Pageable pageable) {
        requireView(condominiumId, entityType);
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("condominiumId"), condominiumId));
            if (category != null) p.add(cb.equal(root.get("category"), category));
            if (entityType != null) p.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) p.add(cb.equal(root.get("entityId"), entityId));
            if (!includeArchived) p.add(cb.isNull(root.get("archivedAt")));
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("fileName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PaginaResponse.of(repository.findAll(spec, pageable), DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse upload(UUID condominiumId, Upload u) {
        Condominio condominium = access.exigir(condominiumId, editPermission(u.entityType()));
        validate(u);
        String extension = extension(u.originalName());
        String key = storage.gravar(u.content());
        var user = access.usuario();
        Document document = repository.save(new Document(condominium.getIdAdministradora(), condominiumId, u.category(),
                u.title().trim(), blank(u.description()), safeName(u.originalName()), ALLOWED.get(extension),
                u.content().length, sha256(u.content()), key,
                u.visibility() == null ? Document.Visibility.INTERNAL : u.visibility(), u.entityType(), u.entityId(),
                u.unitId(), user.idUsuario(), user.nome()));
        auditService.registrar(AcaoAuditoria.DOCUMENT_UPLOADED, "Document", document.getId(), Map.of("title", document.getTitle(),
                "file", document.getFileName(), "visibility", document.getVisibility().name()));
        return DocumentResponse.from(document);
    }

    @Transactional
    public FileContent download(UUID condominiumId, UUID id) {
        Document d = repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        requireView(condominiumId, d.getEntityType());
        auditService.registrar(AcaoAuditoria.DOCUMENT_DOWNLOADED, "Document", id, Map.of("file", d.getFileName()));
        return new FileContent(d.getFileName(), d.getContentType(), storage.ler(d.getStorageKey()));
    }

    /** Entrega para o portal do morador (permissão verificada pelo serviço do portal). */
    public FileContent read(Document d) {
        return new FileContent(d.getFileName(), d.getContentType(), storage.ler(d.getStorageKey()));
    }

    @Transactional
    public DocumentResponse archive(UUID condominiumId, UUID id) {
        Document d = repository.findByIdAndCondominiumId(id, condominiumId).orElseThrow(() -> ApiException.naoEncontrado("Documento"));
        access.exigir(condominiumId, editPermission(d.getEntityType()));
        d.archive(access.usuario().idUsuario(), Instant.now(clock));
        auditService.registrar(AcaoAuditoria.DOCUMENT_ARCHIVED, "Document", id, Map.of("title", d.getTitle()));
        return DocumentResponse.from(d);
    }

    // ------------------------------------------------------------------

    private void requireView(UUID condominiumId, String entityType) {
        if (entityType == null) {
            access.exigir(condominiumId, Permissao.DOCUMENT_VIEW);
            return;
        }
        Permissao[] perms = ENTITY_PERMISSIONS.get(entityType);
        if (perms == null) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY_TYPE", "Tipo de vínculo inválido.");
        }
        access.exigir(condominiumId, accessService.concessoesAtuais().tem(Permissao.DOCUMENT_VIEW, condominiumId)
                ? Permissao.DOCUMENT_VIEW : perms[0]);
    }

    private Permissao editPermission(String entityType) {
        if (entityType == null) {
            return Permissao.DOCUMENT_EDIT;
        }
        Permissao[] perms = ENTITY_PERMISSIONS.get(entityType);
        if (perms == null) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY_TYPE", "Tipo de vínculo inválido.");
        }
        return perms[1];
    }

    private void validate(Upload u) {
        if (u.content() == null || u.content().length == 0) {
            throw ApiException.requisicaoInvalida("EMPTY_FILE", "Arquivo vazio.");
        }
        if (u.content().length > props.storage().maxFileBytes()) {
            throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Arquivo maior que " + (props.storage().maxFileBytes() / (1024 * 1024)) + " MB.");
        }
        if (u.title() == null || u.title().isBlank() || u.title().length() > 200) {
            throw ApiException.requisicaoInvalida("TITLE_REQUIRED", "Informe o título (até 200 caracteres).");
        }
        String ext = extension(u.originalName());
        if (!ALLOWED.containsKey(ext)) {
            throw ApiException.requisicaoInvalida("FILE_TYPE_NOT_ALLOWED", "Tipo de arquivo não permitido. Use PDF, imagem (PNG/JPG), DOCX, XLSX, CSV ou TXT.");
        }
        if (!signatureMatches(ext, u.content())) {
            throw ApiException.requisicaoInvalida("FILE_CONTENT_MISMATCH", "O conteúdo do arquivo não corresponde à extensão informada.");
        }
        if ((u.entityType() == null) != (u.entityId() == null)) {
            throw ApiException.requisicaoInvalida("INVALID_ENTITY", "Informe tipo e identificador do vínculo, ou nenhum dos dois.");
        }
    }

    static boolean signatureMatches(String ext, byte[] c) {
        return switch (ext) {
            case "pdf" -> startsWith(c, 0x25, 0x50, 0x44, 0x46);
            case "png" -> startsWith(c, 0x89, 0x50, 0x4E, 0x47);
            case "jpg", "jpeg" -> startsWith(c, 0xFF, 0xD8, 0xFF);
            case "docx", "xlsx" -> startsWith(c, 0x50, 0x4B, 0x03, 0x04);
            case "csv", "txt" -> {
                for (int i = 0; i < Math.min(c.length, 4096); i++) {
                    if (c[i] == 0) yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    private static boolean startsWith(byte[] c, int... signature) {
        if (c.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((c[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /** Remove caminhos e caracteres de controle do nome original. */
    private static String safeName(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "_");
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
