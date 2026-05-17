package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资源下载消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDownloadMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long resourceId;

    private Long userId;

    private LocalDateTime timestamp;
}
