package com.shiqian.resource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Data
@TableName("t_resource")
public class Resource {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String summary;

    private String contentMarkdown;

    private String contentType;   // ARTICLE / FILE / MIXED

    private Long categoryId;

    // 以下旧字段保留兼容，未来逐步废弃
    private String fileUrl;
    private Long fileSize;
    private String fileType;

    private Integer downloadCount;

    private Integer viewCount;

    private Integer version;

    private Integer status;

    private String reviewReason;

    private Long reviewerId;

    private LocalDateTime reviewTime;

    private String offlineReason;

    private LocalDateTime publishedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    // 第二阶段：附件列表（不映射到数据库）
    @TableField(exist = false)
    private List<ResourceAttachment> attachments;

    // 轻量作者昵称（不映射数据库，通过 ResourceService 页查询富化）
    @TableField(exist = false)
    private String authorNickname;

    // 演示用轻量昵称映射（保持最小变更，无需跨服务调用；生产环境建议通过用户服务批量查询或冗余字段）
    // 改进：为演示数据匹配真实昵称；未知用户回退为友好文案而非暴露ID
    private static final Map<Long, String> NICKNAME_MAP = Map.of(
            1L, "管理员",
            2L, "学生一号"
    );

    /**
     * 轻量方法：为资源列表富化 authorNickname（供 Service 页查询调用）
     */
    public static void enrichAuthors(Collection<Resource> resources) {
        if (resources == null) return;
        for (Resource r : resources) {
            if (r.getAuthorNickname() == null && r.getUserId() != null) {
                r.setAuthorNickname(NICKNAME_MAP.getOrDefault(r.getUserId(), "匿名用户"));
            }
        }
    }
}
