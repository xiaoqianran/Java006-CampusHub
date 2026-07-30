package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            SELECT *
            FROM t_outbox_event
            WHERE status IN ('PENDING', 'FAILED')
              AND next_retry_time <= #{now}
            ORDER BY id
            LIMIT #{limit}
            """)
    List<OutboxEvent> selectReady(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    @Update("""
            UPDATE t_outbox_event
            SET status = 'PUBLISHING',
                update_time = #{now}
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
              AND next_retry_time <= #{now}
            """)
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_outbox_event
            SET status = 'PUBLISHED',
                published_time = #{now},
                last_error = NULL,
                update_time = #{now}
            WHERE id = #{id}
              AND status = 'PUBLISHING'
            """)
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_outbox_event
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_time = #{nextRetryTime},
                last_error = #{lastError},
                update_time = #{now}
            WHERE id = #{id}
              AND status = 'PUBLISHING'
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_outbox_event
            SET status = CASE
                    WHEN retry_count + 1 >= #{maxAttempts} THEN 'DEAD'
                    ELSE 'FAILED'
                END,
                retry_count = retry_count + 1,
                next_retry_time = #{now},
                last_error = 'publisher claim timed out',
                update_time = #{now}
            WHERE status = 'PUBLISHING'
              AND update_time < #{staleBefore}
            """)
    int recoverStaleClaims(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE t_outbox_event
            SET status = 'PENDING',
                retry_count = 0,
                next_retry_time = #{now},
                last_error = NULL,
                update_time = #{now}
            WHERE id = #{id}
              AND status = 'DEAD'
            """)
    int retryDead(@Param("id") Long id, @Param("now") LocalDateTime now);
}
