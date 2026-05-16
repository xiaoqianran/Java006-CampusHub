package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.dto.ResourceUpdateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceMapper resourceMapper;
    private final CategoryService categoryService;

    @Override
    public Resource createResource(Long userId, ResourceCreateDTO dto) {
        Category category = categoryService.getCategoryById(dto.getCategoryId());
        if (category == null || category.getDeleted() == 1) {
            throw new BusinessException("分类不存在");
        }

        Resource resource = new Resource();
        BeanUtils.copyProperties(dto, resource);
        resource.setUserId(userId);
        resource.setDownloadCount(0);
        resource.setVersion(1);
        resource.setStatus(0);

        resourceMapper.insert(resource);
        log.info("资源创建成功: id={}, title={}, userId={}", resource.getId(), resource.getTitle(), userId);
        return resource;
    }

    @Override
    public Resource getResourceById(Long id) {
        return resourceMapper.selectById(id);
    }

    @Override
    public void updateResource(Long userId, Long id, ResourceUpdateDTO dto) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }

        Category category = categoryService.getCategoryById(dto.getCategoryId());
        if (category == null || category.getDeleted() == 1) {
            throw new BusinessException("分类不存在");
        }

        Resource resource = new Resource();
        BeanUtils.copyProperties(dto, resource);
        resource.setId(id);
        resource.setUserId(userId);
        resource.setVersion(existing.getVersion() + 1);
        resource.setDownloadCount(existing.getDownloadCount());
        resource.setStatus(existing.getStatus());

        resourceMapper.updateById(resource);
        log.info("资源更新成功: id={}, title={}, version={}", id, resource.getTitle(), resource.getVersion());
    }

    @Override
    public void deleteResource(Long userId, Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new BusinessException("无权删除该资源");
        }
        resourceMapper.deleteById(id);
        log.info("资源删除成功: id={}, userId={}", id, userId);
    }

    @Override
    public void incrementDownloadCount(Long id) {
        Resource existing = resourceMapper.selectById(id);
        if (existing == null || existing.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        UpdateWrapper<Resource> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        wrapper.setSql("download_count = download_count + 1");
        resourceMapper.update(null, wrapper);
        log.info("资源下载计数增加: id={}", id);
    }

    @Override
    public Page<Resource> pageResources(Integer page, Integer size, Long categoryId, String keyword) {
        Page<Resource> pageParam = new Page<>(page, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("deleted", 0);

        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("title", keyword).or().like("description", keyword));
        }

        wrapper.orderByDesc("create_time");
        return resourceMapper.selectPage(pageParam, wrapper);
    }
}
