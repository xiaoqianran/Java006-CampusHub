package com.shiqian.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.mapper.ResourceAttachmentMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ResourceTransactionIntegrationTest extends BaseResourceTest {

    @Autowired
    private ResourceService resourceService;
    @Autowired
    private ResourceMapper resourceMapper;
    @Autowired
    private ResourceAttachmentMapper attachmentMapper;
    private final List<Long> createdResourceIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long resourceId : createdResourceIds) {
            attachmentMapper.delete(
                    new QueryWrapper<ResourceAttachment>().eq("resource_id", resourceId));
            resourceMapper.physicalDeleteById(resourceId);
        }
        createdResourceIds.clear();
    }

    @Test
    void createMustRollbackResourceWhenAttachmentInsertFails() {
        String title = "事务回滚创建-" + System.nanoTime();
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setContentMarkdown("正文");
        dto.setAttachments(List.of(invalidAttachment()));

        assertThrows(DataIntegrityViolationException.class,
                () -> resourceService.createResource(11L, dto));

        assertEquals(0L, resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("title", title)));
    }

    @Test
    void updateMustRollbackResourceAndAttachmentReplacementTogether() {
        ResourceCreateDTO create = new ResourceCreateDTO();
        create.setTitle("事务回滚更新-旧标题-" + System.nanoTime());
        create.setContentMarkdown("正文");
        create.setAttachments(List.of(validAttachment()));
        Resource saved = resourceService.createResource(11L, create);
        createdResourceIds.add(saved.getId());

        ResourceUpdateDTO update = new ResourceUpdateDTO();
        update.setTitle("事务回滚更新-新标题");
        update.setContentMarkdown("新正文");
        update.setAttachments(List.of(invalidAttachment()));

        assertThrows(DataIntegrityViolationException.class,
                () -> resourceService.updateResource(11L, saved.getId(), update));

        Resource persisted = resourceMapper.selectById(saved.getId());
        assertEquals(create.getTitle(), persisted.getTitle());
        List<ResourceAttachment> attachments = attachmentMapper.selectList(
                new QueryWrapper<ResourceAttachment>().eq("resource_id", saved.getId()));
        assertEquals(1, attachments.size());
        assertEquals("valid.txt", attachments.get(0).getFileName());
    }

    @Test
    void elasticsearchFailureAfterCommitMustNotRollbackMysql() {
        when(resourceDocumentRepository.save(any())).thenThrow(new IllegalStateException("ES unavailable"));
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("ES失败不回滚-" + System.nanoTime());
        dto.setContentMarkdown("正文");

        Resource saved = resourceService.createResource(11L, dto);
        createdResourceIds.add(saved.getId());

        assertEquals(1L, resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("id", saved.getId())));
    }

    private AttachmentCreateDTO validAttachment() {
        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName("valid.txt");
        attachment.setFileUrl("/files/valid.txt");
        attachment.setFileSize(10L);
        return attachment;
    }

    private AttachmentCreateDTO invalidAttachment() {
        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileUrl("/files/invalid.txt");
        attachment.setFileSize(10L);
        return attachment;
    }
}
