package com.atlasia.ai.service.exception.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SafeFileNameValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeFileName {
    String message() default "Filename contains invalid characters or path traversal attempts";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
