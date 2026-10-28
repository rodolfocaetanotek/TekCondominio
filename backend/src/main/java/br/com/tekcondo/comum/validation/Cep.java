package br.com.tekcondo.comum.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** CEP no formato 00000-000 ou 00000000. Nulos/vazios são aceitos. */
@Documented
@Constraint(validatedBy = Cep.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cep {

    String message() default "CEP inválido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Cep, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || DocumentosBrasileiros.isCepValido(valor);
        }
    }
}
