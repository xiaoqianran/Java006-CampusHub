package com.shiqian.resource.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.JimengPromptItem;
import com.shiqian.resource.entity.OutboxEvent;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.OutboxEventMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class JimengIngestServiceIntegrationTest extends BaseResourceTest {

    @Autowired
    private JimengIngestService ingestService;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Test
    void importsGalleryResourceAndCreatesOutboxInSameTransaction() {
        String workId = "jimeng-test-" + UUID.randomUUID();
        JimengPromptItem item = item(workId, "一只站在雪山上的机械猫");

        ingestService.ingestBatch(List.of(item));

        Resource resource = resourceMapper.selectByExternalIdIncludingDeleted("JIMENG", workId);
        assertNotNull(resource);
        assertEquals("GALLERY", resource.getContentScene());
        assertEquals("一只站在雪山上的机械猫", resource.getContentMarkdown());
        assertEquals(1, resource.getStatus());

        List<OutboxEvent> events = outboxEventMapper.selectList(
                new QueryWrapper<OutboxEvent>().eq("aggregate_id", resource.getId()));
        assertEquals(1, events.size());
        assertEquals(OutboxEventType.RESOURCE_CREATED.name(), events.get(0).getEventType());
    }

    @Test
    void keepsDeletedImportedResourceDeletedOnRepeatedSync() {
        String workId = "jimeng-deleted-" + UUID.randomUUID();
        ingestService.ingestBatch(List.of(item(workId, "原提示词")));
        Resource created = resourceMapper.selectByExternalIdIncludingDeleted("JIMENG", workId);
        resourceMapper.deleteById(created.getId());

        ingestService.ingestBatch(List.of(item(workId, "不应覆盖的提示词")));

        Resource deleted = resourceMapper.selectByExternalIdIncludingDeleted("JIMENG", workId);
        assertEquals(1, deleted.getDeleted());
        assertEquals("原提示词", deleted.getContentMarkdown());
        assertTrue(ingestService.findExistingWorkIds(List.of(workId)).contains(workId));
    }

    private JimengPromptItem item(String workId, String prompt) {
        JimengPromptItem item = new JimengPromptItem();
        item.setWorkId(workId);
        item.setPrompt(prompt);
        item.setAuthor("测试作者");
        item.setModel("test-model");
        item.setAspectRatio("1:1");
        return item;
    }
}
