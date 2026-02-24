package com.atlasia.ai.service.exception.validation;

import com.atlasia.ai.service.InputSanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeFileNameValidator implements ConstraintValidator<SafeFileName, String> {

    private InputSanitizationService sanitizationService;

    public SafeFileNameValidator() {
        this.sanitizationService = new InputSanitizationService();
    }

    @Override
    public void initialize(SafeFileName constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return sanitizationService.isValidFileName(value);
    }
}
