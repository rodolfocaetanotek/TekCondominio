package br.com.tekcondo.pessoa.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.pessoa.entity.Pessoa;
import br.com.tekcondo.pessoa.entity.Vinculo;
import br.com.tekcondo.comum.validation.CpfCnpj;
import br.com.tekcondo.comum.validation.Telefone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PessoaDtos {

    private PessoaDtos() {
    }

    public record PessoaRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Máximo de 150 caracteres.") String nome,
            @NotNull(message = "Informe se é pessoa física ou jurídica.") Pessoa.Tipo tipo,
            @CpfCnpj String documento,
            @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @Telefone String telefone,
            @Telefone String telefoneSecundario,
            @Size(max = 500) String observacoes,
            Long versao) {
    }

    public record PessoaResumo(UUID id, String nome, Pessoa.Tipo tipo, String documento, String email, String telefone,
                               boolean ativo, List<String> unidades) {
    }

    public record PessoaDetalhe(UUID id, String nome, Pessoa.Tipo tipo, String documento, String email, String telefone,
                                String telefoneSecundario, String observacoes, boolean ativo, Long versao,
                                boolean usuarioPortal, List<VinculoResponse> vinculos) {
    }

    public record VinculoRequest(
            @NotNull(message = "Informe a unidade.") UUID idUnidade,
            @NotNull(message = "Informe a pessoa.") UUID idPessoa,
            @NotNull(message = "Informe o vínculo.") Vinculo.Tipo tipo,
            boolean responsavelFinanceiro,
            @NotNull(message = "Informe a data de início.") LocalDate dataInicio) {
    }

    public record EncerramentoVinculoRequest(
            @NotNull(message = "Informe a data de encerramento.") LocalDate dataFim,
            @Size(max = 255) String motivo) {
    }

    public record VinculoResponse(UUID id, UUID idCondominio, UUID idUnidade, String rotuloUnidade, UUID idPessoa,
                                  String nomePessoa, Vinculo.Tipo tipo, boolean responsavelFinanceiro,
                                  LocalDate dataInicio, LocalDate dataFim, String motivoEncerramento, boolean vigente) {

        public static VinculoResponse de(Vinculo v) {
            return new VinculoResponse(v.getId(), v.getUnidade().getIdCondominio(), v.getUnidade().getId(),
                    v.getUnidade().rotulo(), v.getPessoa().getId(), v.getPessoa().getNome(), v.getTipo(),
                    v.isResponsavelFinanceiro(), v.getDataInicio(), v.getDataFim(), v.getMotivoEncerramento(),
                    v.isVigente());
        }
    }
}
