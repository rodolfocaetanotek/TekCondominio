package br.com.tekcondo.organization.dto;

import br.com.tekcondo.shared.validation.Cnpj;
import br.com.tekcondo.shared.validation.Phone;
import br.com.tekcondo.shared.validation.StrongPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cadastro de administradora junto com seu primeiro usuário administrador. */
public record OrganizationRequest(
        @NotBlank(message = "Informe o nome.") @Size(max = 150) String name,
        @NotBlank(message = "Informe o CNPJ.") @Cnpj String cnpj,
        @Email(message = "E-mail inválido.") @Size(max = 254) String email,
        @Phone String phone,
        @NotNull(message = "Informe o administrador.") @Valid AdminUser admin) {

    public record AdminUser(
            @NotBlank(message = "Informe o nome do administrador.") @Size(max = 150) String name,
            @NotBlank(message = "Informe o e-mail do administrador.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotBlank(message = "Informe a senha inicial.") @StrongPassword String password) {

        @Override
        public String toString() {
            return "AdminUser[name=" + name + ", email=" + email + ", password=***]";
        }
    }
}
