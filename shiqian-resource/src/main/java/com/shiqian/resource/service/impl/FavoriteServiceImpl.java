package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            throw new BusinessException("已收藏该资源");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setResourceId(resourceId);
        favoriteMapper.insert(favorite);
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
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.eq("resource_id", resourceId);
        return favoriteMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Page<Resource> pageFavorites(Long userId, Integer page, Integer size, String sort) {
        Page<Favorite> favoritePage = new Page<>(page, size);
        QueryWrapper<Favorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        wrapper.orderByDesc("create_time");
        Page<Favorite> favorites = favoriteMapper.selectPage(favoritePage, wrapper);

        Page<Resource> result = new Page<>(favorites.getCurrent(), favorites.getSize(), favorites.getTotal());
        List<Long> resourceIds = favorites.getRecords().stream()
                .map(Favorite::getResourceId)
                .toList();
        if (resourceIds.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        Map<Long, Resource> resourceMap = resourceMapper.selectBatchIds(resourceIds).stream()
                .filter(resource -> resource.getDeleted() == null || resource.getDeleted() == 0)
                // 列表侧也只展示仍处于已发布状态的资源，避免已下架/删除后幽灵卡片。
                .filter(resource -> resource.getStatus() != null && resource.getStatus() == STATUS_PUBLISHED)
                .collect(Collectors.toMap(Resource::getId, Function.identity()));
        List<Resource> recs = resourceIds.stream()
                .map(resourceMap::get)
                .filter(resource -> resource != null)
                .collect(Collectors.toList());
        authorEnrichmentService.enrich(recs);

        if ("hottest".equals(sort)) {
            recs.sort((a, b) -> {
                long hotB = (b.getDownloadCount() != null ? b.getDownloadCount() : 0L) + (b.getViewCount() != null ? b.getViewCount() : 0L);
                long hotA = (a.getDownloadCount() != null ? a.getDownloadCount() : 0L) + (a.getViewCount() != null ? a.getViewCount() : 0L);
                if (hotB != hotA) return Long.compare(hotB, hotA);
                return Long.compare(b.getId() != null ? b.getId() : 0L, a.getId() != null ? a.getId() : 0L);
            });
        }
        // 默认 newest：保留 favorite.create_time 降序（resourceIds 已是该顺序），不再按资源 id 重排。
        result.setRecords(recs);
        return result;
    }
}
