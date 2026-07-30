package com.shiqian.resource;

import com.shiqian.resource.repository.ResourceDocumentRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;
import com.shiqian.resource.security.AccessTokenVersionVerifier;
import org.junit.jupiter.api.BeforeEach;
import io.jsonwebtoken.Claims;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 资源模块测试基类，统一 mock 外部依赖 Bean
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseResourceTest {

    @MockBean
    protected ResourceDocumentRepository resourceDocumentRepository;

    @MockBean
    protected ElasticsearchOperations elasticsearchOperations;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected AccessTokenVersionVerifier accessTokenVersionVerifier;

    @BeforeEach
    void allowCurrentTestTokens() {
        when(accessTokenVersionVerifier.isCurrent(any(Claims.class))).thenReturn(true);
    }
}
