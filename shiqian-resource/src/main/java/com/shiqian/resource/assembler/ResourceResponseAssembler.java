package com.shiqian.resource.assembler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.AdminLog;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.ContentReviewRecord;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceAttachment;
import com.shiqian.resource.entity.SensitiveWord;
import com.shiqian.resource.entity.Tag;
import com.shiqian.resource.vo.AdminLogVO;
import com.shiqian.resource.vo.CategoryVO;
import com.shiqian.resource.vo.ContentReviewRecordVO;
import com.shiqian.resource.vo.ResourceAttachmentVO;
import com.shiqian.resource.vo.ResourceVO;
import com.shiqian.resource.vo.SensitiveWordVO;
import com.shiqian.resource.vo.TagVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class ResourceResponseAssembler {

    public ResourceVO toResourceVO(Resource source) {
        if (source == null) return null;
        ResourceVO target = new ResourceVO();
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setSummary(source.getSummary());
        target.setContentMarkdown(source.getContentMarkdown());
        target.setContentType(source.getContentType());
        target.setContentScene(source.getContentScene());
        target.setTags(source.getTags());
        target.setExternalSource(source.getExternalSource());
        target.setExternalId(source.getExternalId());
        target.setCategoryId(source.getCategoryId());
        target.setFileUrl(source.getFileUrl());
        target.setFileSize(source.getFileSize());
        target.setFileType(source.getFileType());
        target.setDownloadCount(source.getDownloadCount());
        target.setViewCount(source.getViewCount());
        target.setVersion(source.getVersion());
        target.setStatus(source.getStatus());
        target.setReviewReason(source.getReviewReason());
        target.setReviewerId(source.getReviewerId());
        target.setReviewTime(source.getReviewTime());
        target.setOfflineReason(source.getOfflineReason());
        target.setPublishedTime(source.getPublishedTime());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setAttachments(toAttachmentVOs(source.getAttachments()));
        target.setAuthorNickname(source.getAuthorNickname());
        target.setAuthorUsername(source.getAuthorUsername());
        target.setAuthorAvatar(source.getAuthorAvatar());
        target.setCategoryIds(source.getCategoryIds());
        target.setCategoryNames(source.getCategoryNames());
        target.setTagIds(source.getTagIds());
        target.setTagNames(source.getTagNames());
        target.setSearchHighlights(source.getSearchHighlights());
        return target;
    }

    public Page<ResourceVO> toResourcePage(Page<Resource> source) {
        return mapPage(source, this::toResourceVO);
    }

    public ResourceAttachmentVO toAttachmentVO(ResourceAttachment source) {
        if (source == null) return null;
        ResourceAttachmentVO target = new ResourceAttachmentVO();
        target.setId(source.getId());
        target.setResourceId(source.getResourceId());
        target.setFileName(source.getFileName());
        target.setFileUrl(source.getFileUrl());
        target.setFileSize(source.getFileSize());
        target.setFileType(source.getFileType());
        target.setMimeType(source.getMimeType());
        target.setAssetKind(source.getAssetKind());
        target.setUsageType(source.getUsageType());
        target.setSortOrder(source.getSortOrder());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public List<ResourceAttachmentVO> toAttachmentVOs(List<ResourceAttachment> sources) {
        return sources == null ? List.of() : sources.stream()
                .map(this::toAttachmentVO)
                .toList();
    }

    public CategoryVO toCategoryVO(Category source) {
        if (source == null) return null;
        CategoryVO target = new CategoryVO();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setName(source.getName());
        target.setSortOrder(source.getSortOrder());
        target.setIcon(source.getIcon());
        target.setStatus(source.getStatus());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        target.setChildren(source.getChildren() == null
                ? List.of()
                : source.getChildren().stream().map(this::toCategoryVO).toList());
        return target;
    }

    public List<CategoryVO> toCategoryVOs(List<Category> sources) {
        return sources == null ? List.of() : sources.stream()
                .map(this::toCategoryVO)
                .toList();
    }

    public Page<CategoryVO> toCategoryPage(Page<Category> source) {
        return mapPage(source, this::toCategoryVO);
    }

    public TagVO toTagVO(Tag source) {
        if (source == null) return null;
        TagVO target = new TagVO();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setStatus(source.getStatus());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    public List<TagVO> toTagVOs(List<Tag> sources) {
        return sources == null ? List.of() : sources.stream()
                .map(this::toTagVO)
                .toList();
    }

    public AdminLogVO toAdminLogVO(AdminLog source) {
        if (source == null) return null;
        AdminLogVO target = new AdminLogVO();
        target.setId(source.getId());
        target.setOperatorId(source.getOperatorId());
        target.setOperatorName(source.getOperatorName());
        target.setAction(source.getAction());
        target.setTargetType(source.getTargetType());
        target.setTargetId(source.getTargetId());
        target.setDetail(source.getDetail());
        target.setRequestMethod(source.getRequestMethod());
        target.setRequestUri(source.getRequestUri());
        target.setRequestIp(source.getRequestIp());
        target.setRequestParams(source.getRequestParams());
        target.setResult(source.getResult());
        target.setErrorMessage(source.getErrorMessage());
        target.setDurationMs(source.getDurationMs());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public Page<AdminLogVO> toAdminLogPage(Page<AdminLog> source) {
        return mapPage(source, this::toAdminLogVO);
    }

    public SensitiveWordVO toSensitiveWordVO(SensitiveWord source) {
        if (source == null) return null;
        SensitiveWordVO target = new SensitiveWordVO();
        target.setId(source.getId());
        target.setWord(source.getWord());
        target.setLevel(source.getLevel());
        target.setStatus(source.getStatus());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    public List<SensitiveWordVO> toSensitiveWordVOs(List<SensitiveWord> sources) {
        return sources == null ? List.of() : sources.stream()
                .map(this::toSensitiveWordVO)
                .toList();
    }

    public ContentReviewRecordVO toContentReviewRecordVO(ContentReviewRecord source) {
        if (source == null) return null;
        ContentReviewRecordVO target = new ContentReviewRecordVO();
        target.setId(source.getId());
        target.setResourceId(source.getResourceId());
        target.setSubmitterId(source.getSubmitterId());
        target.setReviewerId(source.getReviewerId());
        target.setReviewType(source.getReviewType());
        target.setDecision(source.getDecision());
        target.setMatchedWords(source.getMatchedWords());
        target.setReason(source.getReason());
        target.setContentTitle(source.getContentTitle());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public Page<ContentReviewRecordVO> toContentReviewRecordPage(
            Page<ContentReviewRecord> source) {
        return mapPage(source, this::toContentReviewRecordVO);
    }

    private <S, T> Page<T> mapPage(Page<S> source, Function<S, T> converter) {
        Page<T> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(converter).toList());
        return target;
    }
}
