package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.CounterFlushBatch;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CounterFlushBatchMapper extends BaseMapper<CounterFlushBatch> {

    @Insert("""
            INSERT IGNORE INTO t_counter_flush_batch
              (batch_id, counter_type, status, create_time, update_time)
            VALUES
              (#{batchId}, #{counterType}, 'PROCESSING', #{now}, #{now})
            """)
    int insertIgnore(
            @Param("batchId") String batchId,
            @Param("counterType") String counterType,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_counter_flush_batch
            SET status = 'APPLIED', update_time = #{now}
            WHERE batch_id = #{batchId}
            """)
    int markApplied(@Param("batchId") String batchId, @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM t_counter_flush_batch
            WHERE status = 'APPLIED' AND update_time < #{before}
            """)
    int deleteAppliedBefore(@Param("before") LocalDateTime before);
}
