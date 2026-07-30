package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceIndexMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private Long eventId;
    private String eventType;
    private Long resourceId;
    private LocalDateTime occurredAt;
}
