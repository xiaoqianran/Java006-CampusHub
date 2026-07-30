package com.shiqian.resource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class IndexConsistencyVO {

    private long mysqlPublished;
    private long elasticsearchDocuments;
    private List<Long> missingDocumentIds;
    private List<Long> orphanDocumentIds;

    public boolean isConsistent() {
        return missingDocumentIds.isEmpty() && orphanDocumentIds.isEmpty();
    }
}
