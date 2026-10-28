package br.com.tekcondo.integracao.armazenamento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

import br.com.tekcondo.comum.config.TekCondoProperties;
import org.springframework.stereotype.Component;

/** Armazenamento em disco local (volume persistente em produção). Chaves aleatórias de 128 bits. */
@Component
public class ArmazenamentoArquivosLocal implements ArmazenamentoArquivos {

    private final Path raiz;
    private final SecureRandom random = new SecureRandom();

    public ArmazenamentoArquivosLocal(TekCondoProperties props) {
        this.raiz = props.storage().dir().toAbsolutePath().normalize();
    }

    @Override
    public String gravar(byte[] conteudo) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String chave = HexFormat.of().formatHex(bytes);
        Path alvo = caminho(chave);
        try {
            Files.createDirectories(alvo.getParent());
            Files.write(alvo, conteudo);
            return chave;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        }
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return Files.readAllBytes(caminho(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo não encontrado no armazenamento", e);
        }
    }

    /** Subpastas pelos 2 primeiros caracteres; a chave é validada para impedir travessia de diretórios. */
    private Path caminho(String chave) {
        if (!chave.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return raiz.resolve(chave.substring(0, 2)).resolve(chave);
    }
}
