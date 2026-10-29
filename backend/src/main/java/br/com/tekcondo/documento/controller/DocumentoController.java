package br.com.tekcondo.documento.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import br.com.tekcondo.documento.entity.Documento;
import br.com.tekcondo.documento.service.DocumentoService;
import br.com.tekcondo.documento.service.DocumentoService.ConteudoArquivo;
import br.com.tekcondo.documento.service.DocumentoService.DocumentoResponse;
import br.com.tekcondo.comum.web.PaginaRequests;
import br.com.tekcondo.comum.web.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Documentos do condomínio e anexos de registros de outros módulos (anexo = documento com tipo e id de entidade). */
@RestController
@RequestMapping("/api/v1/condominios/{idCondominio}/documentos")
@Tag(name = "Documentos e anexos")
public class DocumentoController {

    private final DocumentoService service;

    public DocumentoController(DocumentoService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResponse<DocumentoResponse> listar(@PathVariable UUID idCondominio,
                                                    @RequestParam(required = false) Documento.Categoria categoria,
                                                    @RequestParam(required = false) String tipoEntidade,
                                                    @RequestParam(required = false) UUID idEntidade,
                                                    @RequestParam(required = false) String busca,
                                                    @RequestParam(defaultValue = "false") boolean arquivados,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "50") int tamanho) {
        return service.listar(idCondominio, categoria, tipoEntidade, idEntidade, busca, arquivados,
                PaginaRequests.of(pagina, tamanho, null, Map.of(), Sort.by(Sort.Direction.DESC, "criadoEm")));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia documento ou anexo (até 10 MB; PDF, PNG, JPG, DOCX, XLSX, CSV, TXT)")
    public ResponseEntity<DocumentoResponse> enviar(@PathVariable UUID idCondominio, @RequestPart("arquivo") MultipartFile arquivo,
                                                    @RequestParam Documento.Categoria categoria,
                                                    @RequestParam String titulo,
                                                    @RequestParam(required = false) String descricao,
                                                    @RequestParam(required = false) Documento.Visibilidade visibilidade,
                                                    @RequestParam(required = false) String tipoEntidade,
                                                    @RequestParam(required = false) UUID idEntidade,
                                                    @RequestParam(required = false) UUID idUnidade) throws IOException {
        return ResponseEntity.status(201).body(service.enviar(idCondominio, new DocumentoService.EnvioDocumento(categoria, titulo,
                descricao, visibilidade, tipoEntidade, idEntidade, idUnidade, arquivo.getOriginalFilename(), arquivo.getBytes())));
    }

    @GetMapping("/{id}/baixar")
    @Operation(summary = "Baixa o arquivo (somente usuários autorizados; acesso auditado)")
    public ResponseEntity<byte[]> baixar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return comoAnexo(service.baixar(idCondominio, id));
    }

    @PostMapping("/{id}/arquivar")
    public DocumentoResponse arquivar(@PathVariable UUID idCondominio, @PathVariable UUID id) {
        return service.arquivar(idCondominio, id);
    }

    public static ResponseEntity<byte[]> comoAnexo(ConteudoArquivo arquivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(arquivo.tipoConteudo()))
                .body(arquivo.conteudo());
    }
}
