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

/** Telefone brasileiro com DDD (10 ou 11 dígitos), com ou sem máscara. Nulos/vazios são aceitos. */
@Documented
@Constraint(validatedBy = Telefone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Telefone {

    String message() default "Telefone inválido. Informe DDD + número.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Telefone, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || DocumentosBrasileiros.isTelefoneValido(valor);
        }
    }
}
