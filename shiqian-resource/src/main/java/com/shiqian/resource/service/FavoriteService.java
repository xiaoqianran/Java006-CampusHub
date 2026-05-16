package com.shiqian.resource.service;

public interface FavoriteService {

    void addFavorite(Long userId, Long resourceId);

    void removeFavorite(Long userId, Long resourceId);

    boolean isFavorited(Long userId, Long resourceId);
}
