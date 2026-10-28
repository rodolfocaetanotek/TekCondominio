package br.com.tekcondo.shared.validation;

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
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "A senha deve ter ao menos 10 caracteres, com letras e números.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return isStrong(value);
        }

        public static boolean isStrong(String value) {
            return value != null
                    && value.length() >= 10
                    && value.getBytes(StandardCharsets.UTF_8).length <= 72
                    && value.chars().anyMatch(Character::isLetter)
                    && value.chars().anyMatch(Character::isDigit);
        }
    }
}
