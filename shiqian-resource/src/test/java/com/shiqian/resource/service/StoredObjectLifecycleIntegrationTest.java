package com.shiqian.resource.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.FileUploadVO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class StoredObjectLifecycleIntegrationTest extends BaseResourceTest {

    private static final Long OWNER_ID = 991L;

    @Autowired
    private StoredObjectService storedObjectService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceVersionService versionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void objectMustBindArchiveRollbackAndReleasePersistentQuota() {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "readme.txt",
                "text/plain",
                "campushub storage".getBytes(StandardCharsets.UTF_8));
        FileUploadVO upload = storedObjectService.storeFiles(OWNER_ID, List.of(file)).get(0);

        assertEquals("TEMPORARY", statusOf(upload));
        assertEquals(file.getSize(), usedBytes());
        assertThrows(BusinessException.class, () ->
                storedObjectService.bindResourceFiles(
                        OWNER_ID + 1,
                        999L,
                        List.of(upload.getFileUrl())));

        ResourceCreateDTO create = new ResourceCreateDTO();
        create.setTitle("对象存储生命周期");
        create.setContentMarkdown("正文");
        create.setContentScene("SHARE");
        create.setAttachments(List.of(attachment(upload)));
        Resource resource = resourceService.createResource(OWNER_ID, create);

        assertEquals("BOUND", statusOf(upload));
        assertEquals(resource.getId(), resourceIdOf(upload));

        ResourceUpdateDTO update = new ResourceUpdateDTO();
        update.setTitle("移除当前附件");
        update.setContentMarkdown("仅保留正文");
        update.setContentScene("SHARE");
        update.setAttachments(List.of());
        resourceService.updateResource(OWNER_ID, resource.getId(), update);
        assertEquals("ARCHIVED", statusOf(upload));

        versionService.rollback(OWNER_ID, resource.getId(), 1, "恢复带附件的版本");
        assertEquals("BOUND", statusOf(upload));

        storedObjectService.deleteResourceFiles(resource.getId());
        assertEquals("PENDING_DELETE", statusOf(upload));
        assertEquals(0L, usedBytes());
    }

    private AttachmentCreateDTO attachment(FileUploadVO upload) {
        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName(upload.getOriginalName());
        attachment.setFileUrl(upload.getFileUrl());
        attachment.setFileSize(upload.getFileSize());
        attachment.setFileType(upload.getFileType());
        attachment.setMimeType(upload.getMimeType());
        attachment.setAssetKind(upload.getAssetKind());
        return attachment;
    }

    private String publicId(FileUploadVO upload) {
        return upload.getFileUrl().substring(upload.getFileUrl().lastIndexOf('/') + 1);
    }

    private String statusOf(FileUploadVO upload) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM t_stored_object WHERE public_id = ?",
                String.class,
                publicId(upload));
    }

    private Long resourceIdOf(FileUploadVO upload) {
        return jdbcTemplate.queryForObject(
                "SELECT resource_id FROM t_stored_object WHERE public_id = ?",
                Long.class,
                publicId(upload));
    }

    private long usedBytes() {
        Long used = jdbcTemplate.queryForObject(
                "SELECT used_bytes FROM t_user_storage_quota WHERE owner_id = ?",
                Long.class,
                OWNER_ID);
        assertTrue(used != null && used >= 0);
        return used;
    }
}
