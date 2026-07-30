package com.shiqian.resource.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JimengBatchRequest {

    private List<JimengPromptItem> items = new ArrayList<>();
}
