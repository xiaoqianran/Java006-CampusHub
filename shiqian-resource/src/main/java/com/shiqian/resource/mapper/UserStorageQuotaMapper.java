package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.UserStorageQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserStorageQuotaMapper extends BaseMapper<UserStorageQuota> {

    @Insert("""
            INSERT INTO t_user_storage_quota (owner_id, used_bytes, update_time)
            SELECT
                #{ownerId},
                COALESCE((
                    SELECT SUM(COALESCE(a.file_size, 0))
                    FROM t_resource_attachment a
                    JOIN t_resource r ON r.id = a.resource_id
                    WHERE r.user_id = #{ownerId}
                      AND r.deleted = 0
                ), 0)
                + COALESCE((
                    SELECT SUM(COALESCE(r.file_size, 0))
                    FROM t_resource r
                    WHERE r.user_id = #{ownerId}
                      AND r.deleted = 0
                      AND NOT EXISTS (
                          SELECT 1
                          FROM t_resource_attachment a
                          WHERE a.resource_id = r.id
                      )
                ), 0),
                CURRENT_TIMESTAMP
            ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id)
            """)
    int ensureExists(@Param("ownerId") Long ownerId);

    @Select("""
            SELECT owner_id, used_bytes, update_time
            FROM t_user_storage_quota
            WHERE owner_id = #{ownerId}
            FOR UPDATE
            """)
    UserStorageQuota selectForUpdate(@Param("ownerId") Long ownerId);
}
