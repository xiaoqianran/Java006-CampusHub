package com.shiqian.resource.mapper;

import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
public class ResourceMapperTest extends BaseResourceTest {

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    public void testInsert() {
        Resource resource = new Resource();
        resource.setUserId(1L);
        resource.setTitle("测试资源");
        resource.setDescription("测试描述");
        resource.setCategoryId(1L);
        resource.setFileUrl("http://example.com/file.pdf");
        resource.setFileSize(1024L);
        resource.setFileType("application/pdf");
        resource.setDownloadCount(0);
        resource.setVersion(1);
        resource.setStatus(0);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());

        int rows = resourceMapper.insert(resource);
        assertEquals(1, rows);
        assertNotNull(resource.getId());
        assertTrue(resource.getId() > 0);
    }

    @Test
    public void testSelectById() {
        Resource resource = new Resource();
        resource.setUserId(1L);
        resource.setTitle("查询测试");
        resource.setFileUrl("http://example.com/test.txt");
        resource.setFileSize(2048L);
        resource.setDownloadCount(0);
        resource.setVersion(1);
        resource.setStatus(0);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());

        resourceMapper.insert(resource);
        Long id = resource.getId();

        Resource found = resourceMapper.selectById(id);
        assertNotNull(found);
        assertEquals("查询测试", found.getTitle());
        assertEquals(2048L, found.getFileSize());
        assertEquals(0, found.getDeleted());
    }

    @Test
    public void testLogicDelete() {
        Resource resource = new Resource();
        resource.setUserId(1L);
        resource.setTitle("删除测试");
        resource.setFileUrl("http://example.com/delete.txt");
        resource.setFileSize(512L);
        resource.setDownloadCount(0);
        resource.setVersion(1);
        resource.setStatus(0);
        resource.setCreateTime(LocalDateTime.now());
        resource.setUpdateTime(LocalDateTime.now());

        resourceMapper.insert(resource);
        Long id = resource.getId();

        int rows = resourceMapper.deleteById(id);
        assertEquals(1, rows);

        Resource found = resourceMapper.selectById(id);
        assertNull(found);
    }
}
