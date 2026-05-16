package com.shiqian.resource.mapper;

import com.shiqian.resource.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CategoryMapperTest {

    @Autowired
    private CategoryMapper categoryMapper;

    @Test
    public void testInsert() {
        Category category = new Category();
        category.setParentId(0L);
        category.setName("计算机科学");
        category.setSortOrder(1);
        category.setStatus(1);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());

        int rows = categoryMapper.insert(category);
        assertEquals(1, rows);
        assertNotNull(category.getId());
        assertTrue(category.getId() > 0);
    }

    @Test
    public void testSelectById() {
        Category category = new Category();
        category.setParentId(0L);
        category.setName("数学");
        category.setSortOrder(2);
        category.setStatus(1);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());

        categoryMapper.insert(category);
        Long id = category.getId();

        Category found = categoryMapper.selectById(id);
        assertNotNull(found);
        assertEquals("数学", found.getName());
        assertEquals(0L, found.getParentId());
        assertEquals(1, found.getStatus());
        assertEquals(0, found.getDeleted());
    }

    @Test
    public void testLogicDelete() {
        Category category = new Category();
        category.setParentId(0L);
        category.setName("英语");
        category.setSortOrder(3);
        category.setStatus(1);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());

        categoryMapper.insert(category);
        Long id = category.getId();

        int rows = categoryMapper.deleteById(id);
        assertEquals(1, rows);

        Category found = categoryMapper.selectById(id);
        assertNull(found);
    }
}
