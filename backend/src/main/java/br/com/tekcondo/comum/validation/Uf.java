package br.com.tekcondo.comum.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Sigla de UF brasileira. Nulos/vazios são aceitos. */
@Documented
@Constraint(validatedBy = Uf.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Uf {

    Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    String message() default "UF inválida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Uf, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || UFS.contains(valor.trim().toUpperCase(Locale.ROOT));
        }
    }
}
