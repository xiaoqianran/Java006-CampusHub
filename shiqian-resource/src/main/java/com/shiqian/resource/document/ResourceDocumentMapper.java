package com.shiqian.resource.document;

import com.shiqian.resource.entity.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class ResourceDocumentMapper {

    public ResourceDocument fromResource(Resource resource) {
        ResourceDocument doc = new ResourceDocument();
        doc.setId(resource.getId());
        doc.setTitle(resource.getTitle());
        doc.setDescription(StringUtils.hasText(resource.getSummary())
                ? resource.getSummary()
                : resource.getContentMarkdown());
        doc.setFileType(resource.getFileType());
        doc.setContentScene(StringUtils.hasText(resource.getContentScene())
                ? resource.getContentScene().trim().toUpperCase(Locale.ROOT)
                : "SHARE");
        doc.setTags(resource.getTags());
        doc.setCategoryId(resource.getCategoryId());
        doc.setUserId(resource.getUserId());
        doc.setStatus(resource.getStatus());
        return doc;
    }
}
