package com.shiqian.resource.service;

import com.shiqian.resource.dto.JimengPromptItem;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface JimengIngestService {

    Map<String, Object> ingestBatch(List<JimengPromptItem> items);

    List<String> findExistingWorkIds(Collection<String> workIds);
}
