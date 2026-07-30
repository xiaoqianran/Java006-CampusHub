package com.shiqian.resource.mq;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.ResourceAuditMessage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.resource.entity.UserNotification;
import com.shiqian.resource.mapper.UserNotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 资源审核消息监听器测试
 */
class ResourceAuditListenerTest extends BaseResourceTest {

    @Autowired
    private ResourceAuditListener resourceAuditListener;

    @Autowired
    private UserNotificationMapper userNotificationMapper;

    @Test
    void shouldHandleAuditMessage() {
        String messageId = java.util.UUID.randomUUID().toString();
        ResourceAuditMessage message = new ResourceAuditMessage(
                messageId,
                10L,
                1L,
                8L,
                2,
                2L,
                "请补充说明",
                LocalDateTime.now());
        assertDoesNotThrow(() -> resourceAuditListener.onAuditMessage(message));
        assertDoesNotThrow(() -> resourceAuditListener.onAuditMessage(message));
        assertEquals(1L, userNotificationMapper.selectCount(
                new QueryWrapper<UserNotification>().eq("message_id", messageId)));
    }
}
