package com.shiqian.resource.service;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class FavoriteServiceTest extends BaseResourceTest {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CategoryService categoryService;

    @Test
    public void testAddFavoriteSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("测试资源", category.getId());

        favoriteService.addFavorite(1L, resource.getId());

        assertTrue(favoriteService.isFavorited(1L, resource.getId()));
    }

    @Test
    public void testAddFavoriteResourceNotExist() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> favoriteService.addFavorite(1L, 99999L));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testAddFavoriteDuplicate() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("测试资源", category.getId());

        favoriteService.addFavorite(1L, resource.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> favoriteService.addFavorite(1L, resource.getId()));
        assertEquals("已收藏该资源", exception.getMessage());
    }

    @Test
    public void testRemoveFavoriteSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("测试资源", category.getId());

        favoriteService.addFavorite(1L, resource.getId());
        assertTrue(favoriteService.isFavorited(1L, resource.getId()));

        favoriteService.removeFavorite(1L, resource.getId());
        assertFalse(favoriteService.isFavorited(1L, resource.getId()));
    }

    @Test
    public void testRemoveFavoriteNotFavorited() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("测试资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> favoriteService.removeFavorite(1L, resource.getId()));
        assertEquals("未收藏该资源", exception.getMessage());
    }

    @Test
    public void testIsFavoritedFalse() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("测试资源", category.getId());

        assertFalse(favoriteService.isFavorited(1L, resource.getId()));
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
