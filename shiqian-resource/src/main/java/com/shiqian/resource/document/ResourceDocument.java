package com.shiqian.resource.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.annotation.Transient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(indexName = "resource")
public class ResourceDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long resourceId;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private String title;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private String description;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private String markdownContent;

    @Field(type = FieldType.Keyword)
    private String fileType;

    @Field(type = FieldType.Keyword)
    private String contentScene;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Long)
    private List<Long> categoryIds;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private List<String> categoryNames;

    @Field(type = FieldType.Long)
    private List<Long> tagIds;

    @Field(type = FieldType.Text, analyzer = "cjk", searchAnalyzer = "cjk")
    private List<String> tagNames;

    @Field(type = FieldType.Keyword)
    private List<String> tagNameKeys;

    @Field(type = FieldType.Long)
    private Long authorId;

    @Field(type = FieldType.Keyword)
    private String authorName;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer downloadCount;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;

    @Field(type = FieldType.Date)
    private LocalDateTime updateTime;

    @Transient
    private Map<String, List<String>> highlights;
}
