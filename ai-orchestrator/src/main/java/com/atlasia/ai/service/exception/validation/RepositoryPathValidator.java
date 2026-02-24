package com.atlasia.ai.service.exception.validation;

import com.atlasia.ai.service.InputSanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RepositoryPathValidator implements ConstraintValidator<RepositoryPath, String> {

    private InputSanitizationService sanitizationService;

    public RepositoryPathValidator() {
        this.sanitizationService = new InputSanitizationService();
    }

    @Override
    public void initialize(RepositoryPath constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return sanitizationService.validateRepositoryPath(value);
    }
}
