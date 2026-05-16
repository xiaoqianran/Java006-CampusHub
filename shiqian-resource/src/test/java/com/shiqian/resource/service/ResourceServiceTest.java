package com.shiqian.resource.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ResourceServiceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CategoryService categoryService;

    @Test
    public void testCreateResourceSuccess() {
        Category category = new Category();
        category.setName("测试分类");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setDescription("测试描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource resource = resourceService.createResource(1L, dto);
        assertNotNull(resource);
        assertNotNull(resource.getId());

        Resource found = resourceService.getResourceById(resource.getId());
        assertNotNull(found);
        assertEquals("测试资源", found.getTitle());
        assertEquals(0, found.getStatus());
        assertEquals(1, found.getVersion());
        assertEquals(0, found.getDownloadCount());
    }

    @Test
    public void testCreateResourceCategoryNotExist() {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setCategoryId(9999L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));
        assertEquals("分类不存在", exception.getMessage());
    }
}
