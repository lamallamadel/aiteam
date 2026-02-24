package com.atlasia.ai.config;

import java.lang.annotation.*;

/**
 * Marker annotation for entity fields that are encrypted at rest using AES-256-GCM.
 * 
 * Fields annotated with @Encrypted should also use @Convert(converter = EncryptedStringConverter.class)
 * to ensure automatic encryption/decryption via JPA.
 * 
 * Usage:
 * <pre>
 * {@code
 * @Encrypted
 * @Column(name = "sensitive_data_encrypted", columnDefinition = "TEXT")
 * @Convert(converter = EncryptedStringConverter.class)
 * private String sensitiveData;
 * }
 * </pre>
 * 
 * Encryption details:
 * - Algorithm: AES-256-GCM (Galois/Counter Mode)
 * - Key size: 256 bits
 * - IV: Random 12 bytes per encryption
 * - Storage: IV prepended to ciphertext, base64 encoded
 * - Key source: HashiCorp Vault (atlasia.encryption.key)
 * 
 * @see EncryptedStringConverter
 * @see com.atlasia.ai.service.ColumnEncryptionService
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Encrypted {
    /**
     * Optional description of why this field is encrypted.
     */
    String reason() default "";
}
