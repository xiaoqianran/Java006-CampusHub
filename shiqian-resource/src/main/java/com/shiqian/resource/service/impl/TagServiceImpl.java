package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.cache.CacheNames;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.entity.ResourceTag;
import com.shiqian.resource.entity.Tag;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.mapper.ResourceTagMapper;
import com.shiqian.resource.mapper.TagMapper;
import com.shiqian.resource.outbox.OutboxEventType;
import com.shiqian.resource.outbox.OutboxService;
import com.shiqian.resource.outbox.ResourceEventPayload;
import com.shiqian.resource.service.ResourceTaxonomyService;
import com.shiqian.resource.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final ResourceMapper resourceMapper;
    private final ResourceTaxonomyService taxonomyService;
    private final OutboxService outboxService;

    @Override
    public List<Tag> listTags(String keyword) {
        QueryWrapper<Tag> query = new QueryWrapper<Tag>()
                .eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("name");
        if (StringUtils.hasText(keyword)) {
            query.like("name", keyword.trim());
        }
        return tagMapper.selectList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tag addTag(String name) {
        String normalized = normalizeName(name);
        Tag existing = tagMapper.selectByNameIncludingDeleted(normalized);
        if (existing != null) {
            if (existing.getDeleted() != null && existing.getDeleted() == 1) {
                tagMapper.restoreById(existing.getId());
                existing.setDeleted(0);
                existing.setStatus(1);
                return existing;
            }
            throw new BusinessException("标签已存在");
        }
        Tag tag = new Tag();
        tag.setName(normalized);
        tag.setStatus(1);
        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("标签已存在");
        }
        return tag;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public Tag updateTag(Long id, String name) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("标签不存在");
        }
        String normalized = normalizeName(name);
        Tag sameName = tagMapper.selectByNameIncludingDeleted(normalized);
        if (sameName != null && !Objects.equals(sameName.getId(), id)) {
            throw new BusinessException("标签名称已存在");
        }
        existing.setName(normalized);
        try {
            tagMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("标签名称已存在");
        }
        refreshAffectedResources(id);
        return existing;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RESOURCE_DETAIL, allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("标签不存在");
        }
        List<Long> affected = taxonomyService.removeTagRelations(id);
        tagMapper.deleteById(id);
        affected.forEach(resourceId -> outboxService.append(
                OutboxEventType.RESOURCE_UPDATED,
                resourceId,
                ResourceEventPayload.resource(resourceId)));
    }

    private void refreshAffectedResources(Long tagId) {
        List<Long> resourceIds = resourceTagMapper.selectList(
                        new QueryWrapper<ResourceTag>().eq("tag_id", tagId))
                .stream()
                .map(ResourceTag::getResourceId)
                .distinct()
                .toList();
        for (Long resourceId : resourceIds) {
            Resource resource = resourceMapper.selectById(resourceId);
            if (resource == null) {
                continue;
            }
            taxonomyService.enrich(List.of(resource));
            String legacyTags = resource.getTagNames() == null || resource.getTagNames().isEmpty()
                    ? null
                    : String.join(",", resource.getTagNames());
            resourceMapper.update(null, new UpdateWrapper<Resource>()
                    .eq("id", resourceId)
                    .set("tags", legacyTags));
            outboxService.append(
                    OutboxEventType.RESOURCE_UPDATED,
                    resourceId,
                    ResourceEventPayload.resource(resourceId));
        }
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("标签名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 50) {
            throw new BusinessException("标签名称最多50个字符");
        }
        return normalized;
    }
}
