package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源下载消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDownloadMessage {

    private Long resourceId;

    private Long userId;

    private LocalDateTime timestamp;
}
