package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.entity.Favorite;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.FavoriteMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.FavoriteService;
import com.shiqian.resource.service.AuthorEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ResourceMapper resourceMapper;
    private final AuthorEnrichmentService authorEnrichmentService;

    private static final int STATUS_PUBLISHED = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFavorite(Long userId, Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
        }
        // 校园广场仅允许收藏已发布资源，避免待审/下架/拒绝内容进入「我的收藏」。
        if (resource.getStatus() == null || resource.getStatus() != STATUS_PUBLISHED) {
            throw new BusinessException("只能收藏已发布的资源");
        }
        if (isFavorited(userId, resourceId)) {
            // 幂等：重复收藏视为成功，避免并发双击报模糊约束错误。
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setResourceId(resourceId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DataIntegrityViolationException duplicate) {
            // 并发下唯一键冲突：视为已收藏成功。
            log.info("用户收藏资源并发幂等: userId={}, resourceId={}", userId, resourceId);
            return;
        }
        log.info("用户收藏资源成功: userId={}, resourceId={}", userId, resourceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, Long resourceId) {
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("resource_id", resourceId);
        Favorite favorite = favoriteMapper.selectOne(wrapper);
        if (favorite == null) {
            throw new BusinessException("未收藏该资源");
        }
        favoriteMapper.deleteById(favorite.getId());
        log.info("用户取消收藏成功: userId={}, resourceId={}", userId, resourceId);
    }

    @Override
    public boolean isFavorited(Long userId, Long resourceId) {
        // 与列表一致：仅已发布资源算「已收藏」，防止历史脏行或未清理行造成幽灵状态。
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null
                || (resource.getDeleted() != null && resource.getDeleted() == 1)
                || resource.getStatus() == null
                || resource.getStatus() != STATUS_PUBLISHED) {
            return false;
        }
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("resource_id", resourceId);
        return favoriteMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Page<Resource> pageFavorites(Long userId, Integer page, Integer size, String sort) {
        Page<Resource> pageParam = new Page<>(page, size);
        IPage<Resource> queryResult = "hottest".equals(sort)
                ? favoriteMapper.selectPublishedFavoritesPageByHot(pageParam, userId)
                : favoriteMapper.selectPublishedFavoritesPage(pageParam, userId);

        Page<Resource> result = new Page<>(queryResult.getCurrent(), queryResult.getSize(), queryResult.getTotal());
        result.setRecords(queryResult.getRecords());
        authorEnrichmentService.enrich(result.getRecords());
        return result;
    }
}
