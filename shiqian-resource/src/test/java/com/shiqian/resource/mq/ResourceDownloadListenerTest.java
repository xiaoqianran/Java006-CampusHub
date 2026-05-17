package com.shiqian.resource.mq;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.ResourceDownloadMessage;
import com.shiqian.resource.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

/**
 * 资源下载消息监听器测试
 */
class ResourceDownloadListenerTest extends BaseResourceTest {

    @Autowired
    private ResourceDownloadListener resourceDownloadListener;

    @MockBean
    private ResourceService resourceService;

    @Test
    void shouldHandleDownloadMessage() {
        ResourceDownloadMessage message = new ResourceDownloadMessage(1L, 2L, LocalDateTime.now());
        resourceDownloadListener.onDownloadMessage(message);
        verify(resourceService).incrementDownloadCount(1L);
    }
}
