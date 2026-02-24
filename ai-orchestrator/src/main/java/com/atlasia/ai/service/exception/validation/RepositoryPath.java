package com.atlasia.ai.service.exception.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = RepositoryPathValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RepositoryPath {
    String message() default "Repository path must be in format 'owner/repo'";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
