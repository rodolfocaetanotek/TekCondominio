package br.com.tekcondo.integracao.armazenamento;

/**
 * Porta de armazenamento de arquivos. A implementação local grava em disco; em produção pode ser trocada por
 * armazenamento de objetos (S3 compatível) sem alterar os módulos de negócio.
 */
public interface ArmazenamentoArquivos {

    /** Grava o conteúdo e devolve uma chave opaca e não previsível. */
    String gravar(byte[] conteudo);

    byte[] ler(String chave);
}
