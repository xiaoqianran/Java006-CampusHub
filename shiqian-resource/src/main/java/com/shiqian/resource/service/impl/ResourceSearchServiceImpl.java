package com.shiqian.resource.service.impl;

import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.service.ResourceSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceSearchServiceImpl implements ResourceSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ResourceDocument> search(String keyword, Integer page, Integer size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(m -> m
                                .query(keyword)
                                .fields("title", "description")
                        )
                )
                .withPageable(PageRequest.of(page - 1, size))
                .build();

        SearchHits<ResourceDocument> searchHits = elasticsearchOperations.search(query, ResourceDocument.class);
        List<ResourceDocument> documents = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new PageImpl<>(documents, PageRequest.of(page - 1, size), searchHits.getTotalHits());
    }
}
