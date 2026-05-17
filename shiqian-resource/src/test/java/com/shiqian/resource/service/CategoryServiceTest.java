package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class CategoryServiceTest extends BaseResourceTest {

    @Autowired
    private CategoryService categoryService;

    @Test
    public void testAddCategorySuccess() {
        Category category = new Category();
        category.setName("计算机科学");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);

        categoryService.addCategory(category);
        assertNotNull(category.getId());
        assertTrue(category.getId() > 0);
    }

    @Test
    public void testAddCategoryWithParentSuccess() {
        Category parent = new Category();
        parent.setName("理工科");
        parent.setParentId(0L);
        parent.setSortOrder(1);
        parent.setStatus(1);
        categoryService.addCategory(parent);

        Category child = new Category();
        child.setName("计算机科学");
        child.setParentId(parent.getId());
        child.setSortOrder(1);
        child.setStatus(1);
        categoryService.addCategory(child);

        assertNotNull(child.getId());
    }

    @Test
    public void testAddCategoryParentNotExist() {
        Category category = new Category();
        category.setName("不存在父分类");
        category.setParentId(9999L);
        category.setSortOrder(1);
        category.setStatus(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> categoryService.addCategory(category));
        assertEquals("父分类不存在", exception.getMessage());
    }

    @Test
    public void testUpdateCategorySuccess() {
        Category category = new Category();
        category.setName("旧名称");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        Category update = new Category();
        update.setId(category.getId());
        update.setName("新名称");
        update.setSortOrder(2);
        update.setStatus(1);
        categoryService.updateCategory(update);

        Category found = categoryService.getCategoryById(category.getId());
        assertEquals("新名称", found.getName());
        assertEquals(2, found.getSortOrder());
    }

    @Test
    public void testDeleteCategorySuccess() {
        Category category = new Category();
        category.setName("待删除");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        Long id = category.getId();
        categoryService.deleteCategory(id);

        Category found = categoryService.getCategoryById(id);
        assertNull(found);
    }

    @Test
    public void testDeleteCategoryHasChildrenFail() {
        Category parent = new Category();
        parent.setName("父分类");
        parent.setParentId(0L);
        parent.setSortOrder(1);
        parent.setStatus(1);
        categoryService.addCategory(parent);

        Category child = new Category();
        child.setName("子分类");
        child.setParentId(parent.getId());
        child.setSortOrder(1);
        child.setStatus(1);
        categoryService.addCategory(child);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> categoryService.deleteCategory(parent.getId()));
        assertEquals("该分类下存在子分类，无法删除", exception.getMessage());
    }

    @Test
    public void testGetCategoryTree() {
        Category root1 = new Category();
        root1.setName("理工科");
        root1.setParentId(0L);
        root1.setSortOrder(2);
        root1.setStatus(1);
        categoryService.addCategory(root1);

        Category root2 = new Category();
        root2.setName("文科");
        root2.setParentId(0L);
        root2.setSortOrder(1);
        root2.setStatus(1);
        categoryService.addCategory(root2);

        Category child = new Category();
        child.setName("计算机");
        child.setParentId(root1.getId());
        child.setSortOrder(1);
        child.setStatus(1);
        categoryService.addCategory(child);

        List<Category> tree = categoryService.getCategoryTree();
        assertEquals(2, tree.size());
        assertEquals("文科", tree.get(0).getName());
        assertEquals("理工科", tree.get(1).getName());
        assertEquals(1, tree.get(1).getChildren().size());
        assertEquals("计算机", tree.get(1).getChildren().get(0).getName());
    }

    @Test
    public void testPageCategories() {
        for (int i = 1; i <= 5; i++) {
            Category category = new Category();
            category.setName("分类" + i);
            category.setParentId(0L);
            category.setSortOrder(i);
            category.setStatus(1);
            categoryService.addCategory(category);
        }

        Page<Category> page = new Page<>(1, 3);
        Page<Category> result = categoryService.pageCategories(page);

        assertEquals(5, result.getTotal());
        assertEquals(2, result.getPages());
        assertEquals(3, result.getRecords().size());
    }
}
