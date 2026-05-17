package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源审核状态变更消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceAuditMessage {

    private Long resourceId;

    private Integer status;

    private Long operatorId;

    private LocalDateTime timestamp;
}
