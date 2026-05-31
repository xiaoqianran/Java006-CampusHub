package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.common.security.JwtUtil;
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

    @Autowired
    private JwtUtil jwtUtil;

    private String userToken;
    private String adminToken;
    private String guestToken;

    @BeforeEach
    public void setUp() {
        when(resourceDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        userToken = jwtUtil.generateAccessToken(1L, "testuser", "USER");
        adminToken = jwtUtil.generateAccessToken(2L, "admin", "ADMIN");
        guestToken = jwtUtil.generateAccessToken(3L, "guest", "GUEST");
    }

    @Test
    public void testCreateResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("测试正文");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + userToken)
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
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateResourceCategoryNotExist() throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# 测试正文\n\n这是 Markdown 内容。");
        dto.setCategoryId(9999L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分类不存在"));
    }

    @Test
    public void testCreateResourceSensitiveContent() throws Exception {
        Category category = createCategory("测试分类");

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("违规资料");
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# 测试正文\n\n这是 Markdown 内容。");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("资源内容包含敏感词"));
    }

    @Test
    public void testCreateResourceWithoutTokenShouldReturnUnauthorized() throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# 测试正文\n\n这是 Markdown 内容。");
        dto.setCategoryId(1L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    public void testCreateResourceWithoutPermissionShouldReturnForbidden() throws Exception {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# 测试正文\n\n这是 Markdown 内容。");
        dto.setCategoryId(1L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    public void testPageResourcesSuccess() throws Exception {
        Category category = createCategory("测试分类");
        createResource("资源A", category.getId());
        createResource("资源B", category.getId());

        mockMvc.perform(get("/api/resource")
                        .header("Authorization", "Bearer " + adminToken)
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
                        .header("Authorization", "Bearer " + adminToken)
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
                        .header("Authorization", "Bearer " + adminToken)
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
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# 测试正文\n\n这是 Markdown 内容。");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        var page = resourceService.pageResources(1, 1, category.getId(), "详情测试资源");
        Long id = page.getRecords().get(0).getId();

        mockMvc.perform(get("/api/resource/{id}", id)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testGetResourceByIdNotExist() throws Exception {
        mockMvc.perform(get("/api/resource/{id}", 99999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    public void testUpdateResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");

        ResourceCreateDTO createDto = buildCreateDto(category.getId(), "旧标题");
        Resource resource = resourceService.createResource(1L, createDto);

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setSummary("新摘要");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");

        mockMvc.perform(put("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + userToken)
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
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testDeleteResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "删除测试"));

        mockMvc.perform(delete("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}", resource.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    public void testAdminCannotDeleteOtherUserResource() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "管理员不能删除测试"));

        mockMvc.perform(delete("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权删除该资源"));
    }

    @Test
    public void testAdminTakeDownResourceOwnerCanStillView() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "管理员下架测试"));

        mockMvc.perform(put("/api/resource/{id}/audit", resource.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}", resource.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        mockMvc.perform(get("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2));

        mockMvc.perform(get("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testRecycleBinOnlyAdminCanRead() throws Exception {
        mockMvc.perform(get("/api/resource/recycle-bin")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testDeleteResourceNotExist() throws Exception {
        mockMvc.perform(delete("/api/resource/{id}", 99999)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    public void testDownloadResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "下载测试"));

        mockMvc.perform(post("/api/resource/{id}/download", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
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

        mockMvc.perform(post("/api/resource/{id}/download", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
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

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testAddFavoriteDuplicate() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "收藏测试"));

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("已收藏该资源"));
    }

    @Test
    public void testRemoveFavoriteSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "收藏测试"));

        mockMvc.perform(post("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}/favorite", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    public void testAuditResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "审核测试"));

        mockMvc.perform(put("/api/resource/{id}/audit", resource.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void testAuditResourceNotExist() throws Exception {
        mockMvc.perform(put("/api/resource/{id}/audit", 99999)
                        .header("Authorization", "Bearer " + adminToken)
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
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("审核状态不合法"));
    }

    @Test
    public void testAuditResourceWithoutPermissionShouldReturnForbidden() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "审核测试"));

        mockMvc.perform(put("/api/resource/{id}/audit", resource.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .param("status", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    public void testResubmitRejectedResourceSuccess() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "重新提交测试"));
        resourceService.auditResource(resource.getId(), 2, 2L);

        mockMvc.perform(put("/api/resource/{id}/resubmit", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/resource/{id}", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    public void testResubmitPendingResourceShouldFail() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(1L, buildCreateDto(category.getId(), "重新提交测试"));

        mockMvc.perform(put("/api/resource/{id}/resubmit", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("只有已驳回资源可以重新提交"));
    }

    @Test
    public void testResubmitResourceWithoutOwnerPermissionShouldFail() throws Exception {
        Category category = createCategory("测试分类");
        Resource resource = resourceService.createResource(2L, buildCreateDto(category.getId(), "重新提交测试"));
        resourceService.auditResource(resource.getId(), 2, 2L);

        mockMvc.perform(put("/api/resource/{id}/resubmit", resource.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权重新提交该资源"));
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
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# " + title + "\n\n测试正文。");
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
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# " + title + "\n\n测试正文。");
        dto.setCategoryId(categoryId);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        mockMvc.perform(post("/api/resource")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }
}
