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
        Resource resource = createPublishedResource("测试资源", category.getId());

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
    public void testAddFavoriteRejectsPendingResource() {
        Category category = createCategory("测试分类");
        Resource pending = createPendingResource("待审资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> favoriteService.addFavorite(1L, pending.getId()));
        assertEquals("只能收藏已发布的资源", exception.getMessage());
        assertFalse(favoriteService.isFavorited(1L, pending.getId()));
    }

    @Test
    public void testAddFavoriteDuplicateIsIdempotent() {
        Category category = createCategory("测试分类");
        Resource resource = createPublishedResource("测试资源", category.getId());

        favoriteService.addFavorite(1L, resource.getId());
        // 重复收藏应幂等成功，不再抛「已收藏」
        favoriteService.addFavorite(1L, resource.getId());
        assertTrue(favoriteService.isFavorited(1L, resource.getId()));
    }

    @Test
    public void testRemoveFavoriteSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createPublishedResource("测试资源", category.getId());

        favoriteService.addFavorite(1L, resource.getId());
        assertTrue(favoriteService.isFavorited(1L, resource.getId()));

        favoriteService.removeFavorite(1L, resource.getId());
        assertFalse(favoriteService.isFavorited(1L, resource.getId()));
    }

    @Test
    public void testRemoveFavoriteNotFavorited() {
        Category category = createCategory("测试分类");
        Resource resource = createPublishedResource("测试资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> favoriteService.removeFavorite(1L, resource.getId()));
        assertEquals("未收藏该资源", exception.getMessage());
    }

    @Test
    public void testIsFavoritedFalse() {
        Category category = createCategory("测试分类");
        Resource resource = createPublishedResource("测试资源", category.getId());

        assertFalse(favoriteService.isFavorited(1L, resource.getId()));
    }

    @Test
    public void testPageFavoritesHidesUnpublishedResources() {
        Category category = createCategory("测试分类");
        Resource published = createPublishedResource("已发布收藏", category.getId());
        favoriteService.addFavorite(1L, published.getId());

        // 下架后列表不再返回该资源，且收藏行被清理
        resourceService.reviewResource(published.getId(), 4, "违规下架", 2L);
        var page = favoriteService.pageFavorites(1L, 1, 10, "newest");
        assertTrue(page.getRecords().stream().noneMatch(r -> r.getId().equals(published.getId())));
        assertEquals(0, page.getTotal());
        assertFalse(favoriteService.isFavorited(1L, published.getId()));
    }

    @Test
    public void testSoftDeleteResourceClearsFavorites() {
        Category category = createCategory("测试分类");
        Resource published = createPublishedResource("软删收藏", category.getId());
        favoriteService.addFavorite(1L, published.getId());
        assertTrue(favoriteService.isFavorited(1L, published.getId()));

        resourceService.deleteResource(1L, published.getId());
        assertFalse(favoriteService.isFavorited(1L, published.getId()));
        assertEquals(0, favoriteService.pageFavorites(1L, 1, 10, "newest").getTotal());
    }

    @Test
    public void testRejectReviewClearsFavorites() {
        Category category = createCategory("测试分类");
        Resource published = createPublishedResource("审核拒绝收藏", category.getId());
        favoriteService.addFavorite(1L, published.getId());
        assertTrue(favoriteService.isFavorited(1L, published.getId()));

        // 管理员将已发布资源拒绝：收藏应被清理
        resourceService.reviewResource(published.getId(), 3, "内容不符合规范", 2L);
        assertFalse(favoriteService.isFavorited(1L, published.getId()));
        assertEquals(0, favoriteService.pageFavorites(1L, 1, 10, "newest").getTotal());
    }

    @Test
    public void testOwnerEditPublishedClearsFavorites() {
        Category category = createCategory("测试分类");
        Resource published = createPublishedResource("作者改后重审", category.getId());
        favoriteService.addFavorite(1L, published.getId());
        assertTrue(favoriteService.isFavorited(1L, published.getId()));

        var dto = new com.shiqian.resource.dto.ResourceUpdateDTO();
        dto.setTitle("作者修改后的标题");
        dto.setSummary("新摘要");
        dto.setContentMarkdown("# 改写正文\n\n需要重新审核。");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("/api/resource/files/1/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");
        resourceService.updateResource(1L, published.getId(), dto);

        // 避开缓存，直接查库
        Resource refreshed = resourceService.getResourceById(published.getId());
        assertNotNull(refreshed);
        // status may be cached; isFavorited/pageFavorites already assert business outcome
        assertFalse(favoriteService.isFavorited(1L, published.getId()));
        assertEquals(0, favoriteService.pageFavorites(1L, 1, 10, "newest").getTotal());
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

    private Resource createPendingResource(String title, Long categoryId) {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setCategoryId(categoryId);
        dto.setFileUrl("/api/resource/files/1/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        return resourceService.createResource(1L, dto);
    }

    private Resource createPublishedResource(String title, Long categoryId) {
        Resource resource = createPendingResource(title, categoryId);
        resourceService.reviewResource(resource.getId(), 1, null, 2L);
        Resource refreshed = resourceService.getResourceById(resource.getId());
        assertNotNull(refreshed);
        assertEquals(1, refreshed.getStatus());
        return refreshed;
    }
}
