package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    public void testCreateResourceSuccess() throws Exception {
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
}
