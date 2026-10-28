package br.com.tekcondo.integration.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

import br.com.tekcondo.shared.config.TekCondoProperties;
import org.springframework.stereotype.Component;

/** Armazenamento em disco local (volume persistente em produção). Chaves aleatórias de 128 bits. */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final SecureRandom random = new SecureRandom();

    public LocalFileStorage(TekCondoProperties props) {
        this.root = props.storage().dir().toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo", e);
        }
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo não encontrado no armazenamento", e);
        }
    }

    /** Subpastas pelos 2 primeiros caracteres; a chave é validada para impedir travessia de diretórios. */
    private Path resolve(String key) {
        if (!key.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return root.resolve(key.substring(0, 2)).resolve(key);
    }
}
