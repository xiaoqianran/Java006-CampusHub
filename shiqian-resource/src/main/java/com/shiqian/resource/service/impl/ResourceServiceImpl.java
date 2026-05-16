package com.shiqian.resource.service.impl;

import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.ResourceCreateDTO;
import com.shiqian.resource.entity.Category;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.CategoryService;
import com.shiqian.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
}
