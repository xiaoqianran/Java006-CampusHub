package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;

import java.util.List;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.isA;

@Transactional
public class ResourceServiceTest extends BaseResourceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CategoryService categoryService;

    private final Map<Long, ResourceDocument> esDocs = new HashMap<>();

    @BeforeEach
    public void setUp() {
        esDocs.clear();

        when(resourceDocumentRepository.save(any(ResourceDocument.class)))
                .thenAnswer(inv -> {
                    ResourceDocument doc = inv.getArgument(0);
                    esDocs.put(doc.getId(), doc);
                    return doc;
                });

        when(resourceDocumentRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(esDocs.get(inv.getArgument(0))));

        doAnswer(inv -> {
            esDocs.remove(inv.getArgument(0));
            return null;
        }).when(resourceDocumentRepository).deleteById(anyLong());
        doAnswer(inv -> {
            esDocs.clear();
            return null;
        }).when(resourceDocumentRepository).deleteAll();
    }

    @Test
    public void testCreateResourceSuccess() {
        Category category = new Category();
        category.setName("测试分类");
        category.setParentId(0L);
        category.setSortOrder(1);
        category.setStatus(1);
        categoryService.addCategory(category);

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("测试描述");
        dto.setContentMarkdown("测试描述正文");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        Resource resource = resourceService.createResource(1L, dto);
        assertNotNull(resource);
        assertNotNull(resource.getId());

        Resource found = resourceService.getResourceById(resource.getId());
        assertNotNull(found);
        assertEquals("测试资源", found.getTitle());
        assertEquals(0, found.getStatus());
        assertEquals(1, found.getVersion());
        assertEquals(0, found.getDownloadCount());
    }

    @Test
    public void testCreateFileOnlyResourceWithoutMarkdown() {
        Category category = createCategory("附件分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("附件型资源");
        dto.setCategoryId(category.getId());
        dto.setContentType("FILE");

        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName("课程讲义.pdf");
        attachment.setFileUrl("http://example.com/lecture.pdf");
        attachment.setFileSize(2048L);
        attachment.setFileType("application/pdf");
        dto.setAttachments(List.of(attachment));

        Resource created = resourceService.createResource(1L, dto);
        Resource found = resourceService.getResourceById(created.getId());

        assertEquals("FILE", found.getContentType());
        assertNull(found.getContentMarkdown());
        assertEquals(1, found.getAttachments().size());
    }

    @Test
    public void testAnyChannelStillRequiresAtLeastOneContentSource() {
        Category category = createCategory("文章分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("空文章");
        dto.setCategoryId(category.getId());
        dto.setContentType("ARTICLE");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));

        assertEquals("请至少填写正文、上传图片或添加一个附件", exception.getMessage());
    }

    @Test
    public void testCreateResourceRequiresTextOrAttachment() {
        Category category = createCategory("空内容分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("缺少内容的资源");
        dto.setCategoryId(category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));

        assertEquals("请至少填写正文、上传图片或添加一个附件", exception.getMessage());
    }

    @Test
    public void testCreateContentWithoutCategoryAndWithFreeScene() {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("无分类图片帖");
        dto.setContentScene("GALLERY");
        dto.setTags("校园,摄影");

        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName("campus.jpg");
        attachment.setFileUrl("http://example.com/campus.jpg");
        attachment.setFileSize(2048L);
        attachment.setFileType("image/jpeg");
        attachment.setAssetKind("IMAGE");
        dto.setAttachments(List.of(attachment));

        Resource created = resourceService.createResource(1L, dto);
        Resource found = resourceService.getResourceById(created.getId());

        assertNull(found.getCategoryId());
        assertEquals("GALLERY", found.getContentScene());
        assertEquals("校园,摄影", found.getTags());
        assertEquals("FILE", found.getContentType());
    }

    @Test
    public void testPageResourcesByContentScene() {
        ResourceCreateDTO blog = new ResourceCreateDTO();
        blog.setTitle("博客内容");
        blog.setContentMarkdown("正文");
        blog.setContentScene("BLOG");
        resourceService.createResource(1L, blog);

        ResourceCreateDTO share = new ResourceCreateDTO();
        share.setTitle("资料内容");
        share.setContentMarkdown("说明");
        share.setContentScene("SHARE");
        resourceService.createResource(1L, share);

        Page<Resource> result = resourceService.pageResources(
                1, 10, null, null, null, "BLOG");

        assertEquals(1, result.getTotal());
        assertEquals("BLOG", result.getRecords().get(0).getContentScene());
    }

    @Test
    public void testCreateResourceCategoryNotExist() {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setCategoryId(9999L);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));
        assertEquals("分类不存在", exception.getMessage());
    }

    @Test
    public void testCreateResourceSensitiveTitle() {
        Category category = createCategory("测试分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("违规资料");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));
        assertEquals("资源内容包含敏感词", exception.getMessage());
    }

    @Test
    public void testCreateResourceSensitiveDescription() {
        Category category = createCategory("测试分类");
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("测试资源");
        dto.setSummary("包含敏感词的描述");
        dto.setContentMarkdown("包含敏感词的正文");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.createResource(1L, dto));
        assertEquals("资源内容包含敏感词", exception.getMessage());
    }

    @Test
    public void testPageResourcesNoCondition() {
        Category category = createCategory("测试分类");
        for (int i = 1; i <= 5; i++) {
            createResource("资源" + i, category.getId());
        }

        Page<Resource> page = resourceService.pageResources(1, 3, null, null, null);
        assertEquals(5, page.getTotal());
        assertEquals(2, page.getPages());
        assertEquals(3, page.getRecords().size());
    }

    @Test
    public void testPageResourcesByCategoryId() {
        Category c1 = createCategory("分类1");
        Category c2 = createCategory("分类2");
        createResource("资源A", c1.getId());
        createResource("资源B", c2.getId());
        createResource("资源C", c2.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, c2.getId(), null, null);
        assertEquals(2, page.getTotal());
    }

    @Test
    public void testPageResourcesByKeyword() {
        Category category = createCategory("测试分类");
        createResource("Java入门", category.getId());
        createResource("Python入门", category.getId());
        createResource("Go语言", category.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, null, "入门", null);
        assertEquals(2, page.getTotal());
    }

    @Test
    public void testPageResourcesCombinedCondition() {
        Category c1 = createCategory("分类1");
        Category c2 = createCategory("分类2");
        createResource("Java入门", c1.getId());
        createResource("Python入门", c2.getId());
        createResource("Go语言", c2.getId());

        Page<Resource> page = resourceService.pageResources(1, 10, c2.getId(), "入门", null);
        assertEquals(1, page.getTotal());
        assertEquals("Python入门", page.getRecords().get(0).getTitle());
    }

    @Test
    public void testCreatePureMarkdownResourceAndKeywordSearch() {
        Category category = createCategory("Markdown测试分类");

        // 纯 Markdown 资源（无附件）
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("纯文本 Markdown 资源测试");
        dto.setSummary("这是一个包含 markdown-summary-keyword 的摘要，用于搜索测试");
        dto.setContentMarkdown("# 正文标题\n\n这里包含特殊关键词 markdown-content-keyword 用于验证全文搜索。\n\n支持代码块等。");
        dto.setCategoryId(category.getId());
        // 故意不设置 file 相关字段，测试 Service 兜底
        // dto.setFileUrl(null); dto.setFileSize(null); dto.setFileType(null);

        Resource created = resourceService.createResource(1L, dto);

        // 断言创建成功 + 字段正确保存
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("纯文本 Markdown 资源测试", created.getTitle());
        assertEquals("这是一个包含 markdown-summary-keyword 的摘要，用于搜索测试", created.getSummary());
        assertTrue(created.getContentMarkdown().contains("markdown-content-keyword"));
        assertEquals("ARTICLE", created.getContentType());
        // file 兜底
        assertEquals("", created.getFileUrl());
        assertEquals(0L, created.getFileSize());
        // Service 当前兜底为 "文字资源"（当 fileType 为空时）
        assertEquals("Markdown资源", created.getFileType());

        // 验证关键词能命中 summary
        Page<Resource> pageBySummary = resourceService.pageResources(1, 10, null, "markdown-summary-keyword", null);
        assertTrue(pageBySummary.getTotal() >= 1);
        assertTrue(pageBySummary.getRecords().stream().anyMatch(r -> r.getId().equals(created.getId())));

        // 验证关键词能命中 contentMarkdown
        Page<Resource> pageByContent = resourceService.pageResources(1, 10, null, "markdown-content-keyword", null);
        assertTrue(pageByContent.getTotal() >= 1);
        assertTrue(pageByContent.getRecords().stream().anyMatch(r -> r.getId().equals(created.getId())));
    }

    @Test
    public void testCreateResourceWithAttachments() {
        Category category = createCategory("带附件测试分类");

        AttachmentCreateDTO att1 = new AttachmentCreateDTO();
        att1.setFileName("课件.pdf");
        att1.setFileUrl("https://example.com/files/课件.pdf");
        att1.setFileSize(2048000L);
        att1.setFileType("pdf");
        att1.setMimeType("application/pdf");
        att1.setAssetKind("DOCUMENT");
        att1.setUsageType("ATTACHMENT");

        AttachmentCreateDTO att2 = new AttachmentCreateDTO();
        att2.setFileName("示例代码.zip");
        att2.setFileUrl("https://example.com/files/code.zip");
        att2.setFileSize(512000L);
        att2.setAssetKind("ARCHIVE");

        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("带两个附件的 Markdown 资源");
        dto.setSummary("包含多个附件的示例资源");
        dto.setContentMarkdown("# 说明\n\n这是一个带附件的资源。");
        dto.setCategoryId(category.getId());
        dto.setAttachments(List.of(att1, att2));

        Resource created = resourceService.createResource(1L, dto);

        assertNotNull(created);
        assertNotNull(created.getId());

        // 查询详情验证附件是否落库
        Resource detail = resourceService.getResourceById(created.getId());
        assertNotNull(detail.getAttachments());
        assertEquals(2, detail.getAttachments().size());
        assertEquals("课件.pdf", detail.getAttachments().get(0).getFileName());
        assertEquals("示例代码.zip", detail.getAttachments().get(1).getFileName());
    }

    @Test
    public void testUpdateResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());
        Long id = resource.getId();

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setSummary("新摘要");
        dto.setContentMarkdown("# 新正文\n\n更新后的 Markdown 内容。");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");

        resourceService.updateResource(1L, id, dto);

        Resource updated = resourceService.getResourceById(id);
        assertEquals("新标题", updated.getTitle());
        assertEquals("新摘要", updated.getSummary());
        assertEquals(2, updated.getVersion());
    }

    @Test
    public void testUpdateResourceNotExist() {
        Category category = createCategory("测试分类");
        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.updateResource(1L, 99999L, dto));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testUpdateResourceCategoryNotExist() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setCategoryId(99999L);
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.updateResource(1L, resource.getId(), dto));
        assertEquals("分类不存在", exception.getMessage());
    }

    @Test
    public void testUpdateResourceSensitiveDescription() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setSummary("这是一条广告描述");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.updateResource(1L, resource.getId(), dto));
        assertEquals("资源内容包含敏感词", exception.getMessage());
    }

    @Test
    public void testDeleteResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待删除资源", category.getId());
        Long id = resource.getId();

        resourceService.deleteResource(1L, id);

        Resource found = resourceService.getResourceById(id);
        assertNull(found);
        Page<Resource> recyclePage = resourceService.pageRecycleResources(1, 10, "待删除资源");
        assertEquals(1, recyclePage.getRecords().size());
        assertEquals(id, recyclePage.getRecords().get(0).getId());
    }

    @Test
    public void testDeleteResourceNotExist() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.deleteResource(1L, 99999L));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testDeleteResourceNoPermission() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("他人资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.deleteResource(2L, resource.getId()));
        assertEquals("无权删除该资源", exception.getMessage());
    }

    @Test
    public void testIncrementDownloadCountSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("下载测试资源", category.getId());
        assertEquals(0, resource.getDownloadCount());

        resourceService.incrementDownloadCount(resource.getId());

        Resource updated = resourceService.getResourceById(resource.getId());
        assertEquals(1, updated.getDownloadCount());
    }

    @Test
    public void testIncrementDownloadCountNotExist() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.incrementDownloadCount(99999L));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testCreateDoesNotWriteIndexInsideTransaction() {
        Category category = createCategory("测试分类");
        createResource("ES同步测试", category.getId());

        verify(resourceDocumentRepository, never()).save(any(ResourceDocument.class));
    }

    @Test
    public void testUpdateDoesNotWriteIndexInsideTransaction() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("旧标题", category.getId());

        ResourceUpdateDTO dto = new ResourceUpdateDTO();
        dto.setTitle("新标题");
        dto.setSummary("新摘要");
        dto.setCategoryId(category.getId());
        dto.setFileUrl("http://example.com/new.pdf");
        dto.setFileSize(2048L);
        dto.setFileType("application/pdf");

        resourceService.updateResource(1L, resource.getId(), dto);

        verify(resourceDocumentRepository, never()).save(any(ResourceDocument.class));
    }

    @Test
    public void testDeleteDoesNotWriteIndexInsideTransaction() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("删除测试", category.getId());
        Long id = resource.getId();

        resourceService.deleteResource(1L, id);

        verify(resourceDocumentRepository, never()).deleteById(anyLong());
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

    @Test
    public void testAuditResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待审核资源", category.getId());
        assertEquals(0, resource.getStatus());

        resourceService.auditResource(resource.getId(), 1, 2L);

        Resource updated = resourceService.getResourceById(resource.getId());
        assertEquals(1, updated.getStatus());
    }

    @Test
    public void testGetPublishedResourcesByIdsUsesOneOrderedBatch() {
        Category category = createCategory("搜索批量查询分类");
        Resource first = createResource("第一条", category.getId());
        Resource second = createResource("第二条", category.getId());
        Resource pending = createResource("仍在审核", category.getId());
        resourceService.auditResource(first.getId(), 1, 2L);
        resourceService.auditResource(second.getId(), 1, 2L);

        List<Resource> result = resourceService.getPublishedResourcesByIds(
                List.of(second.getId(), pending.getId(), first.getId(), 99999L));

        assertEquals(2, result.size());
        assertEquals(second.getId(), result.get(0).getId());
        assertEquals(first.getId(), result.get(1).getId());
    }

    @Test
    public void testReviewResourceRequiresReasonAndPersistsFeedback() {
        Category category = createCategory("审核分类");
        Resource resource = createResource("需要修改的资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.reviewResource(resource.getId(), 2, " ", 2L));
        assertEquals("退回、拒绝或下架时必须填写原因", exception.getMessage());

        resourceService.reviewResource(resource.getId(), 2, "请补充课程来源和版本说明", 2L);

        Resource updated = resourceService.getResourceById(resource.getId());
        assertEquals(2, updated.getStatus());
        assertEquals("请补充课程来源和版本说明", updated.getReviewReason());
        assertEquals(2L, updated.getReviewerId());
    }

    @Test
    public void testAuditResourceNotExist() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.auditResource(99999L, 1, 1L));
        assertEquals("资源不存在", exception.getMessage());
    }

    @Test
    public void testAuditResourceInvalidStatus() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待审核资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.auditResource(resource.getId(), 99, 1L));
        assertEquals("审核状态不合法", exception.getMessage());
    }

    @Test
    public void testAuditDoesNotSendMessageBeforeTransactionCommit() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待审核资源", category.getId());

        resourceService.auditResource(resource.getId(), 2, 3L);

        verify(rabbitTemplate, never()).convertAndSend(
                eq("resource.topic"),
                eq("resource.audit"),
                isA(com.shiqian.resource.dto.ResourceAuditMessage.class));
    }

    @Test
    public void testResubmitRejectedResourceSuccess() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("已驳回资源", category.getId());
        resourceService.auditResource(resource.getId(), 2, 2L);

        resourceService.resubmitResource(1L, resource.getId());

        Resource updated = resourceService.getResourceById(resource.getId());
        assertEquals(0, updated.getStatus());
    }

    @Test
    public void testResubmitOnlyRejectedResource() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("待审核资源", category.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.resubmitResource(1L, resource.getId()));
        assertEquals("只有待修改资源可以重新提交", exception.getMessage());
    }

    @Test
    public void testResubmitResourceWithoutOwnerPermission() {
        Category category = createCategory("测试分类");
        Resource resource = createResource("已驳回资源", category.getId());
        resourceService.auditResource(resource.getId(), 2, 2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resourceService.resubmitResource(99L, resource.getId()));
        assertEquals("无权重新提交该资源", exception.getMessage());
    }

    private Resource createResource(String title, Long categoryId) {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle(title);
        dto.setSummary("测试摘要");
        dto.setContentMarkdown("# " + title + "\n\n测试正文。");
        dto.setCategoryId(categoryId);
        dto.setFileUrl("http://example.com/file.pdf");
        dto.setFileSize(1024L);
        dto.setFileType("application/pdf");
        return resourceService.createResource(1L, dto);
    }
}
