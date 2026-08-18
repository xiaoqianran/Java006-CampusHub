package com.shiqian.resource.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoredObjectUrlValidationTest extends BaseResourceTest {

    @Autowired
    private StoredObjectService storedObjectService;

    @Test
    void rejectsArbitraryExternalUrl() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> storedObjectService.validateUserSubmittedFileUrls(
                        1L, List.of("https://evil.example/tracker.png")));

        assertEquals("附件地址必须来自平台上传接口", error.getMessage());
    }

    @Test
    void rejectsProtocolRelativeExternalUrl() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> storedObjectService.validateUserSubmittedFileUrls(
                        1L, List.of("//evil.example/tracker.png")));

        assertEquals("附件地址必须来自平台上传接口", error.getMessage());
    }

    @Test
    void rejectsOtherUsersLegacyAttachment() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> storedObjectService.validateUserSubmittedFileUrls(
                        1L, List.of("/api/resource/files/2/private.pdf")));

        assertEquals(403, error.getCode());
    }

    @Test
    void acceptsPlatformAndOwnedLegacyPaths() {
        assertDoesNotThrow(() -> storedObjectService.validateUserSubmittedFileUrls(
                1L,
                List.of(
                        "/api/resource/files/object/123e4567-e89b-12d3-a456-426614174000",
                        "/api/resource/files/1/legacy.pdf")));
    }
}
