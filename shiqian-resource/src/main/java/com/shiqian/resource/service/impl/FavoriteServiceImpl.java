package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.entity.Favorite;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.FavoriteMapper;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ResourceMapper resourceMapper;

    @Override
    public void addFavorite(Long userId, Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException("资源不存在");
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
}
