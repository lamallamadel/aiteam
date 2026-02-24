package com.atlasia.ai.service.exception.validation;

import com.atlasia.ai.service.InputSanitizationService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeHtmlValidator implements ConstraintValidator<SafeHtml, String> {

    private InputSanitizationService sanitizationService;

    public SafeHtmlValidator() {
        this.sanitizationService = new InputSanitizationService();
    }

    @Override
    public void initialize(SafeHtml constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return sanitizationService.isSafeHtml(value);
    }
}
