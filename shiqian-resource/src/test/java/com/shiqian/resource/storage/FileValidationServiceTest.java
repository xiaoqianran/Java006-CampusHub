package com.shiqian.resource.storage;

import com.shiqian.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidationServiceTest {

    @Test
    void mustRejectFileOverConfiguredLimit() {
        FileValidationService validator = new FileValidationService(4, "txt,pdf");
        MockMultipartFile file = new MockMultipartFile(
                "files", "large.txt", "text/plain", "12345".getBytes(StandardCharsets.UTF_8));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> validator.validate(file));

        assertEquals("large.txt：超过4B", error.getMessage());
    }

    @Test
    void mustUseDetectedMimeInsteadOfTrustingFilename() {
        FileValidationService validator = new FileValidationService(1024, "pdf");
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "document.pdf",
                "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));

        ValidatedFile validated = validator.validate(file);

        assertEquals("application/pdf", validated.mimeType());
        assertEquals("DOCUMENT", validated.assetKind());
    }
}
