package br.com.tekcondo.integration.storage;

/**
 * Porta de armazenamento de arquivos. A implementação local grava em disco; em produção pode ser trocada por
 * armazenamento de objetos (S3 compatível) sem alterar os módulos de negócio.
 */
public interface FileStorage {

    /** Grava o conteúdo e devolve uma chave opaca e não previsível. */
    String store(byte[] content);

    byte[] read(String key);
}
