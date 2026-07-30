package com.shiqian.resource.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 即梦油猴脚本同步载荷（兼容 work_id / image_url 等字段）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JimengPromptItem {

    @JsonAlias({"workId", "work_id"})
    private String workId;

    private String prompt;

    private String author;

    private String model;

    @JsonAlias({"createTime", "create_time"})
    private Long createTime;

    @JsonAlias({"collectedAt", "collected_at"})
    private String collectedAt;

    @JsonAlias({"imageUrl", "image_url"})
    private String imageUrl;

    @JsonAlias({"imageHigh", "image_high"})
    private String imageHigh;

    @JsonAlias({"aspectRatio", "aspect_ratio"})
    private String aspectRatio;
}
