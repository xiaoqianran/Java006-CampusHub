package com.shiqian.resource.mq;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.ResourceIndexMessage;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.MessageIdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceIndexListenerTest extends BaseResourceTest {

    @Autowired
    private ResourceIndexListener listener;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private MessageIdempotencyService idempotencyService;

    private final List<Long> resourceIds = new ArrayList<>();

    @AfterEach
    void cleanResources() {
        resourceIds.forEach(resourceMapper::physicalDeleteById);
    }

    @Test
    void duplicateMessageMustOnlyApplyIndexOnce() {
        Resource resource = publishedResource();
        when(resourceDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ResourceIndexMessage message = message(resource.getId(), "RESOURCE_AUDITED");

        listener.onIndexMessage(message);
        listener.onIndexMessage(message);

        verify(resourceDocumentRepository, times(1)).save(any());
    }

    @Test
    void missingOrDeletedResourceMustDeleteElasticsearchDocument() {
        ResourceIndexMessage message = message(9_999_991L, "RESOURCE_DELETED");

        listener.onIndexMessage(message);
        listener.onIndexMessage(message);

        verify(resourceDocumentRepository, times(1)).deleteById(9_999_991L);
    }

    @Test
    void elasticsearchFailureMustNotAcknowledgeIdempotencyRecord() {
        Resource resource = publishedResource();
        ResourceIndexMessage message = message(resource.getId(), "RESOURCE_UPDATED");
        doThrow(new IllegalStateException("ES unavailable"))
                .when(resourceDocumentRepository).save(any());

        assertThrows(IllegalStateException.class, () -> listener.onIndexMessage(message));
        assertThrows(IllegalStateException.class, () -> listener.onIndexMessage(message));

        verify(resourceDocumentRepository, times(2)).save(any());
        assertFalse(idempotencyService.isConsumed(
                message.getMessageId(), ResourceIndexListener.CONSUMER_NAME));
    }

    private Resource publishedResource() {
        Resource resource = new Resource();
        resource.setUserId(1L);
        resource.setTitle("索引消费测试");
        resource.setSummary("摘要");
        resource.setContentMarkdown("正文");
        resource.setContentType("ARTICLE");
        resource.setContentScene("BLOG");
        resource.setFileSize(0L);
        resource.setFileType("Markdown资源");
        resource.setDownloadCount(0);
        resource.setViewCount(0);
        resource.setVersion(1);
        resource.setStatus(1);
        resource.setDeleted(0);
        resourceMapper.insert(resource);
        resourceIds.add(resource.getId());
        return resource;
    }

    private ResourceIndexMessage message(Long resourceId, String eventType) {
        return new ResourceIndexMessage(
                UUID.randomUUID().toString(),
                1L,
                eventType,
                resourceId,
                LocalDateTime.now());
    }
}
