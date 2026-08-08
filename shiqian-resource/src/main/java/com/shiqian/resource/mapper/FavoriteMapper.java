package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.entity.Favorite;
import com.shiqian.resource.entity.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 仅返回仍已发布且未删除的收藏资源，分页 total 与 records 一致。
     */
    @Select("""
            SELECT r.*
            FROM t_resource r
            INNER JOIN t_favorite f ON f.resource_id = r.id
            WHERE f.user_id = #{userId}
              AND r.deleted = 0
              AND r.status = 1
            ORDER BY f.create_time DESC, r.id DESC
            """)
    IPage<Resource> selectPublishedFavoritesPage(Page<Resource> page, @Param("userId") Long userId);

    @Select("""
            SELECT r.*
            FROM t_resource r
            INNER JOIN t_favorite f ON f.resource_id = r.id
            WHERE f.user_id = #{userId}
              AND r.deleted = 0
              AND r.status = 1
            ORDER BY (COALESCE(r.download_count, 0) + COALESCE(r.view_count, 0)) DESC, r.id DESC
            """)
    IPage<Resource> selectPublishedFavoritesPageByHot(Page<Resource> page, @Param("userId") Long userId);
}
