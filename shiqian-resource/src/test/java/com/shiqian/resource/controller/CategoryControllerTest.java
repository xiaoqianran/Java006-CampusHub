package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.CategoryDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
public class CategoryControllerTest extends BaseResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setUpToken() {
        adminToken = jwtUtil.generateAccessToken(1L, "admin", "ADMIN");
    }

    /** 通用成功响应消息，对应 Result.ok() 返回的 message，避免魔法值 */
    private static final String SUCCESS_MESSAGE = "操作成功";

    @Test
    public void testAddCategorySuccess() throws Exception {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("计算机科学");
        dto.setParentId(0L);
        dto.setSortOrder(1);
        dto.setStatus(1);

        mockMvc.perform(post("/api/category")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE));
    }

    @Test
    public void testAddCategoryValidationFail() throws Exception {
        CategoryDTO dto = new CategoryDTO();
        dto.setName("");
        dto.setParentId(-1L);
        dto.setSortOrder(-1);
        dto.setStatus(2);

        mockMvc.perform(post("/api/category")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdateCategorySuccess() throws Exception {
        Category category = new Category();
        category.setName("旧名称");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        CategoryDTO dto = new CategoryDTO();
        dto.setName("新名称");
        dto.setParentId(0L);
        dto.setSortOrder(2);
        dto.setStatus(1);

        mockMvc.perform(put("/api/category/{id}", category.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE));
    }

    @Test
    public void testDeleteCategorySuccess() throws Exception {
        Category category = new Category();
        category.setName("待删除");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        mockMvc.perform(delete("/api/category/{id}", category.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value(SUCCESS_MESSAGE));
    }

    @Test
    public void testGetCategoryByIdSuccess() throws Exception {
        Category category = new Category();
        category.setName("查询测试");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        mockMvc.perform(get("/api/category/{id}", category.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("查询测试"));
    }

    @Test
    public void testGetCategoryTreeSuccess() throws Exception {
        Category root = new Category();
        root.setName("理工科");
        root.setParentId(0L);
        root.setSortOrder(1);
        root.setStatus(1);
        categoryService.addCategory(root);

        Category child = new Category();
        child.setName("计算机");
        child.setParentId(root.getId());
        child.setSortOrder(1);
        child.setStatus(1);
        categoryService.addCategory(child);

        mockMvc.perform(get("/api/category/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("理工科"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("计算机"));
    }

    @Test
    public void testPageCategoriesSuccess() throws Exception {
        for (int i = 1; i <= 3; i++) {
            Category category = new Category();
            category.setName("分类" + i);
            category.setParentId(0L);
            category.setSortOrder(i);
            category.setStatus(1);
            categoryService.addCategory(category);
        }

        mockMvc.perform(get("/api/category")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }
}
