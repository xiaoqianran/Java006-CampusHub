package com.shiqian.resource.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Resource;

public interface FavoriteService {

    void addFavorite(Long userId, Long resourceId);

    void removeFavorite(Long userId, Long resourceId);

    boolean isFavorited(Long userId, Long resourceId);

    Page<Resource> pageFavorites(Long userId, Integer page, Integer size);
}
