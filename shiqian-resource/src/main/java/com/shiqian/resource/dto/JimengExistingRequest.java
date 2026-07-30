package com.shiqian.resource.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JimengExistingRequest {

    @JsonAlias({"workIds", "work_ids"})
    private List<String> workIds = new ArrayList<>();
}
