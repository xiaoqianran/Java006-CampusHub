package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
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

    @Test
    public void testPageResourcesNoCondition() {
        Category category = createCategory("测试分类");
        for (int i = 1; i <= 5; i++) {
            createResource("资源" + i, category.getId());
        }

        Page<Resource> page = resourceService.pageResources(1, 3, null, null);
        assertEquals(5, page.getTotal());
        assertEquals(2, page.getPages());
        assertEquals(3, page.getRecords().size());
    }

    @Test
    public void testPageResourcesByCategoryId() {
        Category c1 = createCategory("分类1");
        Category c2 = createCategory("分类2");
        createResource("资源A", c1.getId());
        createResource("资源B", c2.getId());
        createResource("资源C", c2.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, c2.getId(), null);
        assertEquals(2, page.getTotal());
    }

    @Test
    public void testPageResourcesByKeyword() {
        Category category = createCategory("测试分类");
        createResource("Java入门", category.getId());
        createResource("Python入门", category.getId());
        createResource("Go语言", category.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, null, "入门");
        assertEquals(2, page.getTotal());
    }

    @Test
    public void testPageResourcesCombinedCondition() {
        Category c1 = createCategory("分类1");
        Category c2 = createCategory("分类2");
        createResource("Java入门", c1.getId());
        createResource("Python入门", c2.getId());
        createResource("Go语言", c2.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, c2.getId(), "入门");
        assertEquals(1, page.getTotal());
        assertEquals("Python入门", page.getRecords().get(0).getTitle());
    }

    @Test
    public void testUpdateResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());
        Long id = resource.getId();

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setDescription("新描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");

        resourceService.updateResource(1L, id, dto);

        Resource updated = resourceService.getResourceById(id);
        assertEquals("新标题", updated.getTitle());
        assertEquals("新描述", updated.getDescription());
        assertEquals(2, updated.getVersion());
    }

    @Test
    public void testUpdateResourceNotExist() {
        Category category = createCategory("测试分类");
        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.updateResource(1L, 99999L, dto));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testUpdateResourceCategoryNotExist() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setCategoryId(99999L);
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.updateResource(1L, resource.getId(), dto));
        assertEquals("分类不存在", exception.getMessage());
    }

    @Test
    public void testDeleteResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待删除资源", category.getId());
        Long id = resource.getId();

        resourceService.deleteResource(1L, id);

        Resource found = resourceService.getResourceById(id);
        assertNull(found);
    }

    @Test
    public void testDeleteResourceNotExist() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.deleteResource(1L, 99999L));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testDeleteResourceNoPermission() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("他人资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.deleteResource(2L, resource.getId()));
        assertEquals("无权删除该资源", exception.getMessage());
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);
        return category;
    }

    private Resource createResource(String title, Long categoryId) {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setCategoryId(categoryId);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        return resourceService.createResource(1L, dto);
    }
}
