package com.shiqian.resource.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class MyBatisPlusPageConfigTest {

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Test
    public void testMybatisPlusInterceptorBeanExists() {
        assertNotNull(mybatisPlusInterceptor, "MybatisPlusInterceptor Bean 应被正确注册到 Spring 容器");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPaginationInnerInterceptorAdded() throws Exception {
        assertNotNull(mybatisPlusInterceptor);

        Field interceptorsField = MybatisPlusInterceptor.class.getDeclaredField("interceptors");
        interceptorsField.setAccessible(true);
        List<?> interceptors = (List<?>) interceptorsField.get(mybatisPlusInterceptor);

        assertNotNull(interceptors, "拦截器列表不应为 null");
        assertFalse(interceptors.isEmpty(), "拦截器链至少应包含一个拦截器");

        boolean hasPagination = interceptors.stream()
                .anyMatch(i -> i instanceof PaginationInnerInterceptor);
        assertTrue(hasPagination, "拦截器链中应包含 PaginationInnerInterceptor");
    }
}
