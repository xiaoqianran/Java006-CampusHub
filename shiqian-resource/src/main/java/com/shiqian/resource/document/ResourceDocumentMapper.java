package com.shiqian.resource.document;

import com.shiqian.resource.entity.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.List;

@Component
public class ResourceDocumentMapper {

    public ResourceDocument fromResource(Resource resource) {
        ResourceDocument doc = new ResourceDocument();
        doc.setId(resource.getId());
        doc.setResourceId(resource.getId());
        doc.setTitle(resource.getTitle());
        doc.setSummary(resource.getSummary());
        doc.setDescription(resource.getDescription());
        doc.setMarkdownContent(resource.getContentMarkdown());
        doc.setFileType(resource.getFileType());
        doc.setContentScene(StringUtils.hasText(resource.getContentScene())
                ? resource.getContentScene().trim().toUpperCase(Locale.ROOT)
                : "SHARE");
        doc.setResourceType(resource.getContentType());
        doc.setCategoryIds(resource.getCategoryIds() == null
                ? (resource.getCategoryId() == null ? List.of() : List.of(resource.getCategoryId()))
                : resource.getCategoryIds());
        doc.setCategoryNames(resource.getCategoryNames() == null
                ? List.of()
                : resource.getCategoryNames());
        doc.setTagIds(resource.getTagIds() == null ? List.of() : resource.getTagIds());
        doc.setTagNames(resource.getTagNames() == null ? List.of() : resource.getTagNames());
        doc.setTagNameKeys(doc.getTagNames().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList());
        doc.setAuthorId(resource.getUserId());
        doc.setAuthorName(StringUtils.hasText(resource.getAuthorNickname())
                ? resource.getAuthorNickname()
                : resource.getAuthorUsername());
        doc.setStatus(resource.getStatus());
        doc.setViewCount(resource.getViewCount());
        doc.setDownloadCount(resource.getDownloadCount());
        doc.setCreateTime(resource.getCreateTime());
        doc.setUpdateTime(resource.getUpdateTime());
        return doc;
    }
}
