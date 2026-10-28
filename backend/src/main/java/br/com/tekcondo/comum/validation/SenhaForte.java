package br.com.tekcondo.comum.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Política mínima de senha: 10+ caracteres, ao menos uma letra e um dígito, até 72 bytes (limite do BCrypt).
 */
@Documented
@Constraint(validatedBy = SenhaForte.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaForte {

    String message() default "A senha deve ter ao menos 10 caracteres, com letras e números.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SenhaForte, String> {
        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return isForte(valor);
        }

        public static boolean isForte(String valor) {
            return valor != null
                    && valor.length() >= 10
                    && valor.getBytes(StandardCharsets.UTF_8).length <= 72
                    && valor.chars().anyMatch(Character::isLetter)
                    && valor.chars().anyMatch(Character::isDigit);
        }
    }
}
