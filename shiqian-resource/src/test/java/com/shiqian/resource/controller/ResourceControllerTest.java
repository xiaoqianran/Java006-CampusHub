package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.resource.service.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class ResourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ResourceService resourceService;

    @Test
    public void testCreateResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setDescription("测试描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testCreateResourceValidationFail() throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("");
        dto.setCategoryId(0L);
        dto.setFileUrl("");
        dto.setFileSize(-1L);
        dto.setFileType("");

        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateResourceCategoryNotExist() throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setCategoryId(9999L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分类不存在"));
    }

    @Test
    public void testPageResourcesSuccess() throws Exception {
        Category category = createCategory("测试分类");
        createResource("资源A", category.getId());
        createResource("资源B", category.getId());

        mockMvc.perform(get("/api/resource")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    public void testPageResourcesByCategoryId() throws Exception {
        Category c1 = createCategory("分类1");
        Category c2 = createCategory("分类2");
        createResource("资源A", c1.getId());
        createResource("资源B", c2.getId());
        createResource("资源C", c2.getId());

        mockMvc.perform(get("/api/resource")
                        .param("page", "1")
                        .param("size", "10")
                        .param("categoryId", String.valueOf(c2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    public void testPageResourcesByKeyword() throws Exception {
        Category category = createCategory("测试分类");
        createResource("Java入门", category.getId());
        createResource("Python入门", category.getId());
        createResource("Go语言", category.getId());

        mockMvc.perform(get("/api/resource")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "入门"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    public void testGetResourceByIdSuccess() throws Exception {
        Category category = createCategory("测试分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("详情测试资源");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resource/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testGetResourceByIdNotExist() throws Exception {
        mockMvc.perform(get("/api/resource/{id}", 99999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    public void testUpdateResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");

        ResourceCreateDTO createDto = new ResourceCreateDTO();
        createDto.setTitle("旧标题");
        createDto.setCategoryId(category.getId());
        createDto.setFileUrl("http://example.com/file.pdf");
        createDto.setFileSize(1024L);
        createDto.setFileType("application/pdf");
        Resource resource = resourceService.createResource(1L, createDto);

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setDescription("新描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");

        mockMvc.perform(put("/api/resource/{id}", resource.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testUpdateResourceValidationFail() throws Exception {
        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("");
        dto.setCategoryId(0L);
        dto.setFileUrl("");
        dto.setFileSize(-1L);
        dto.setFileType("");

        mockMvc.perform(put("/api/resource/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
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

    private void createResource(String title, Long categoryId) throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setCategoryId(categoryId);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
