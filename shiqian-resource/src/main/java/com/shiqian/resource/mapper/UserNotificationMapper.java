package com.shiqian.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shiqian.resource.entity.UserNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {

    @Insert("""
            INSERT IGNORE INTO t_user_notification
                (message_id, user_id, notification_type, title, content,
                 related_id, read_flag, create_time)
            VALUES
                (#{notification.messageId}, #{notification.userId},
                 #{notification.notificationType}, #{notification.title},
                 #{notification.content}, #{notification.relatedId},
                 #{notification.readFlag}, #{notification.createTime})
            """)
    int insertIgnore(@Param("notification") UserNotification notification);
}
