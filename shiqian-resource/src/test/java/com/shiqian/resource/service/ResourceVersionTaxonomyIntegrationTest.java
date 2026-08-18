package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.BaseResourceTest;
import com.shiqian.resource.dto.AttachmentCreateDTO;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.dto.ResourceVersionVO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class ResourceVersionTaxonomyIntegrationTest extends BaseResourceTest {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceVersionService versionService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Test
    void updateAndRollbackMustVersionContentTaxonomyAndAttachmentsTogether() {
        Category java = category("Java");
        Category database = category("数据库");
        Resource created = resourceService.createResource(
                1L,
                createDto(java.getId(), database.getId()));

        List<ResourceVersionVO> initial = versionService.listVersions(1L, created.getId());
        assertEquals(List.of(1), initial.stream().map(ResourceVersionVO::getVersionNumber).toList());

        ResourceUpdateDTO update = new ResourceUpdateDTO();
        update.setTitle("第二版");
        update.setSummary("第二版摘要");
        update.setContentMarkdown("第二版正文");
        update.setContentScene("BLOG");
        update.setCategoryIds(List.of(database.getId()));
        update.setTagNames(List.of("Spring"));
        update.setAttachments(List.of(attachment("second.pdf")));
        update.setChangeDescription("更新正文和附件");
        resourceService.updateResource(1L, created.getId(), update);

        List<ResourceVersionVO> updated = versionService.listVersions(1L, created.getId());
        assertEquals(List.of(2, 1), updated.stream()
                .map(ResourceVersionVO::getVersionNumber)
                .toList());
        assertEquals("second.pdf", updated.get(0).getAttachments().get(0).getFileName());

        Resource rolledBack = versionService.rollback(
                1L,
                created.getId(),
                1,
                "恢复初始稿");
        assertEquals(3, rolledBack.getVersion());
        assertEquals(0, rolledBack.getStatus());

        Resource detail = resourceService.getResourceById(created.getId());
        assertEquals("第一版", detail.getTitle());
        assertEquals(List.of(java.getId(), database.getId()), detail.getCategoryIds());
        assertEquals(List.of("Java", "课程设计"), detail.getTagNames());
        assertEquals("first.pdf", detail.getAttachments().get(0).getFileName());
        assertEquals(List.of(3, 2, 1), versionService.listVersions(1L, created.getId()).stream()
                .map(ResourceVersionVO::getVersionNumber)
                .toList());
    }

    @Test
    void categoryTagAndChannelFiltersMustUseNormalizedRelations() {
        Category primary = category("主分类");
        Category secondary = category("次分类");
        Resource created = resourceService.createResource(
                1L,
                createDto(primary.getId(), secondary.getId()));
        resourceService.auditResource(created.getId(), 1, 2L);

        Page<Resource> bySecondary = resourceService.pagePublishedResources(
                1, 10, secondary.getId(), null, null, "BLOG", null, null);
        Page<Resource> byTag = resourceService.pagePublishedResources(
                1, 10, null, null, null, "BLOG", null, "课程设计");

        assertEquals(1, bySecondary.getTotal());
        assertEquals(created.getId(), bySecondary.getRecords().get(0).getId());
        assertEquals(1, byTag.getTotal());
    }

    @Test
    void deletingCategoryAndTagMustCleanRelationsAndCompatibilityMirrors() {
        Category first = category("待删分类");
        Category second = category("保留分类");
        Resource created = resourceService.createResource(
                1L,
                createDto(first.getId(), second.getId()));
        Tag java = tagService.listTags(null).stream()
                .filter(tag -> "Java".equals(tag.getName()))
                .findFirst()
                .orElseThrow();

        categoryService.deleteCategory(first.getId());
        tagService.deleteTag(java.getId());

        Resource detail = resourceService.getResourceById(created.getId());
        assertEquals(List.of(second.getId()), detail.getCategoryIds());
        assertEquals(second.getId(), detail.getCategoryId());
        assertFalse(detail.getTagNames().contains("Java"));
        assertTrue(detail.getTagNames().contains("课程设计"));
    }

    private ResourceCreateDTO createDto(Long firstCategory, Long secondCategory) {
        ResourceCreateDTO dto = new ResourceCreateDTO();
        dto.setTitle("第一版");
        dto.setSummary("第一版摘要");
        dto.setContentMarkdown("第一版正文");
        dto.setContentScene("BLOG");
        dto.setCategoryIds(List.of(firstCategory, secondCategory));
        dto.setTagNames(List.of("Java", "课程设计"));
        dto.setAttachments(List.of(attachment("first.pdf")));
        return dto;
    }

    private AttachmentCreateDTO attachment(String fileName) {
        AttachmentCreateDTO attachment = new AttachmentCreateDTO();
        attachment.setFileName(fileName);
        attachment.setFileUrl("/api/resource/files/1/" + fileName);
        attachment.setFileSize(128L);
        attachment.setFileType("application/pdf");
        attachment.setMimeType("application/pdf");
        return attachment;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name + System.nanoTime());
        category.setParentId(0L);
        categoryService.addCategory(category);
        return category;
    }
}
