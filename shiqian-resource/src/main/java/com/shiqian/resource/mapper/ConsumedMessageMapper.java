package com.shiqian.resource.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConsumedMessageMapper {

    @Insert("""
            INSERT IGNORE INTO t_mq_consumed_message
                (message_id, consumer_name, create_time)
            VALUES
                (#{messageId}, #{consumerName}, CURRENT_TIMESTAMP)
            """)
    int insertIgnore(
            @Param("messageId") String messageId,
            @Param("consumerName") String consumerName);

    @Select("""
            SELECT COUNT(1)
            FROM t_mq_consumed_message
            WHERE message_id = #{messageId}
              AND consumer_name = #{consumerName}
            """)
    long count(
            @Param("messageId") String messageId,
            @Param("consumerName") String consumerName);
}
