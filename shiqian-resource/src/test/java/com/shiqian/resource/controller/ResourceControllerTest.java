package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.resource.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
public class ResourceControllerTest extends BaseResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ResourceService resourceService;

    @BeforeEach
    public void setUp() {
        when(resourceDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

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

    @Test
    public void testDeleteResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "删除测试"));

        mockMvc.perform(delete("/api/resource/{id}", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    public void testDeleteResourceNotExist() throws Exception {
        mockMvc.perform(delete("/api/resource/{id}", 99999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    public void testDownloadResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "下载测试"));

        mockMvc.perform(post("/api/resource/{id}/download", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(rabbitTemplate).convertAndSend(
                eq("resource.topic"),
                eq("resource.download"),
                any(com.shiqian.resource.dto.ResourceDownloadMessage.class));
    }

    @Test
    public void testDownloadResourceSendMessage() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "下载测试"));

        mockMvc.perform(post("/api/resource/{id}/download", resource.getId()))
                .andExpect(status().isOk());

        verify(rabbitTemplate).convertAndSend(
                eq("resource.topic"),
                eq("resource.download"),
                any(com.shiqian.resource.dto.ResourceDownloadMessage.class));
    }

    @Test
    public void testAddFavoriteSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "收藏测试"));

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testAddFavoriteDuplicate() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "收藏测试"));

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("已收藏该资源"));
    }

    @Test
    public void testRemoveFavoriteSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "收藏测试"));

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}/favorite", resource.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    public void testAuditResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "审核测试"));

        mockMvc.perform(put("/api/resource/{id}/audit", resource.getId())
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testAuditResourceNotExist() throws Exception {
        mockMvc.perform(put("/api/resource/{id}/audit", 99999)
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    public void testAuditResourceInvalidStatus() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "审核测试"));

        mockMvc.perform(put("/api/resource/{id}/audit", resource.getId())
                        .param("status", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("审核状态不合法"));
    }

    @Test
    @Disabled("需要 Elasticsearch 服务")
    public void testSearchResourceByKeyword() throws Exception {
        Category category = createCategory("测试分类");
        resourceService.createResource(1L, buildCreateDto(category.getId(), "Java入门教程"));
        resourceService.createResource(1L, buildCreateDto(category.getId(), "Python高级编程"));
        resourceService.createResource(1L, buildCreateDto(category.getId(), "Go语言实战"));

        elasticsearchOperations.indexOps(ResourceDocument.class).refresh();

        mockMvc.perform(get("/api/resource/search")
                        .param("keyword", "Java")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Java入门教程"));
    }

    private ResourceCreateDTO buildCreateDto(Long categoryId, String title) {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setCategoryId(categoryId);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        return dto;
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
