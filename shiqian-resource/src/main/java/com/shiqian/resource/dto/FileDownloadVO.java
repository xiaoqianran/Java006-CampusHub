package com.shiqian.resource.dto;

import com.shiqian.resource.vo.ResourceAttachmentVO;
import lombok.Data;

import java.util.List;

@Data
public class FileDownloadVO {

    private Long resourceId;

    private String title;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    private List<ResourceAttachmentVO> attachments;
}
