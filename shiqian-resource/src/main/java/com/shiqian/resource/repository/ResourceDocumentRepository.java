package com.shiqian.resource.repository;

import com.shiqian.resource.document.ResourceDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceDocumentRepository extends ElasticsearchRepository<ResourceDocument, Long> {
}
