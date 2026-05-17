package com.shiqian.resource.mq;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.ResourceAuditMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 资源审核消息监听器测试
 */
class ResourceAuditListenerTest extends BaseResourceTest {

    @Autowired
    private ResourceAuditListener resourceAuditListener;

    @Test
    void shouldHandleAuditMessage() {
        ResourceAuditMessage message = new ResourceAuditMessage(
                1L, 1, 2L, LocalDateTime.now());
        assertDoesNotThrow(() -> resourceAuditListener.onAuditMessage(message));
    }
}
