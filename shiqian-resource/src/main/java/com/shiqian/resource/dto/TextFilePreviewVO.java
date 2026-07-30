package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextFilePreviewVO {

    private String content;
    private boolean truncated;
    private long fileSize;
}
