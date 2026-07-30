package com.shiqian.resource.outbox;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.OutboxEvent;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.OutboxEventMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxIntegrationTest extends BaseResourceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    private final List<Long> resourceIds = new ArrayList<>();

    @BeforeEach
    void clearStaleOutboxRowsFromSharedH2Database() {
        outboxEventMapper.delete(new QueryWrapper<>());
    }

    @AfterEach
    void cleanUp() {
        outboxEventMapper.delete(new QueryWrapper<>());
        resourceIds.forEach(resourceMapper::physicalDeleteById);
        resourceIds.clear();
    }

    @Test
    void resourceMutationsMustPersistExpectedEventsInSameTransaction() {
        ResourceCreateDTO create = new ResourceCreateDTO();
        create.setTitle("Outbox 资源");
        create.setContentMarkdown("初始正文");
        Resource resource = resourceService.createResource(1L, create);
        resourceIds.add(resource.getId());

        ResourceUpdateDTO update = new ResourceUpdateDTO();
        update.setTitle("Outbox 资源更新");
        update.setContentMarkdown("更新正文");
        resourceService.updateResource(1L, resource.getId(), update);
        resourceService.reviewResource(resource.getId(), 1, null, 2L);
        resourceService.deleteResource(1L, resource.getId());

        List<OutboxEvent> events = outboxEventMapper.selectList(
                new QueryWrapper<OutboxEvent>()
                        .eq("aggregate_id", resource.getId())
                        .orderByAsc("id"));
        assertEquals(
                List.of(
                        OutboxEventType.RESOURCE_CREATED.name(),
                        OutboxEventType.RESOURCE_UPDATED.name(),
                        OutboxEventType.RESOURCE_AUDITED.name(),
                        OutboxEventType.RESOURCE_DELETED.name()),
                events.stream().map(OutboxEvent::getEventType).toList());
        events.forEach(event -> {
            assertNotNull(event.getMessageId());
            assertEquals(OutboxStatus.PENDING.name(), event.getStatus());
            assertNotNull(event.getPayload());
        });
    }

    @Test
    void failedBusinessWriteMustRollbackResourceAndOutboxTogether() {
        long resourcesBefore = resourceMapper.selectCount(new QueryWrapper<>());
        long eventsBefore = outboxEventMapper.selectCount(new QueryWrapper<>());

        ResourceCreateDTO create = new ResourceCreateDTO();
        create.setTitle("Outbox 回滚");
        create.setContentMarkdown("正文");
        AttachmentCreateDTO invalid = new AttachmentCreateDTO();
        invalid.setFileUrl("/invalid/no-name.txt");
        create.setAttachments(List.of(invalid));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> resourceService.createResource(1L, create));

        assertEquals(resourcesBefore, resourceMapper.selectCount(new QueryWrapper<>()));
        assertEquals(eventsBefore, outboxEventMapper.selectCount(new QueryWrapper<>()));
    }

    @Test
    void repeatedlyStalePublishingClaimMustEventuallyBecomeDead() {
        OutboxEvent event = new OutboxEvent();
        event.setMessageId("stale-" + System.nanoTime());
        event.setEventType(OutboxEventType.RESOURCE_UPDATED.name());
        event.setAggregateType("RESOURCE");
        event.setAggregateId(99L);
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PUBLISHING.name());
        event.setRetryCount(1);
        event.setNextRetryTime(LocalDateTime.now());
        event.setCreateTime(LocalDateTime.now().minusMinutes(10));
        event.setUpdateTime(LocalDateTime.now().minusMinutes(10));
        outboxEventMapper.insert(event);

        outboxEventMapper.recoverStaleClaims(
                LocalDateTime.now().minusMinutes(2), LocalDateTime.now(), 2);

        OutboxEvent recovered = outboxEventMapper.selectById(event.getId());
        assertEquals(OutboxStatus.DEAD.name(), recovered.getStatus());
        assertEquals(2, recovered.getRetryCount());
    }
}
