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
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceSearchServiceImpl implements ResourceSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<ResourceDocument> search(String keyword, Integer page, Integer size, String sort) {
        String text = StringUtils.hasText(keyword) ? keyword.trim() : "";
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s.multiMatch(m -> m
                                        .query(text)
                                        .fields("title^3", "description^2", "fileType")
                                        .fuzziness("AUTO")
                                ))
                                .should(s -> s.matchPhrase(m -> m
                                        .field("title")
                                        .query(text)
                                        .boost(4.0f)
                                ))
                                .should(s -> s.matchPhrase(m -> m
                                        .field("description")
                                        .query(text)
                                        .boost(2.0f)
                                ))
                                .should(s -> s.wildcard(w -> w
                                        .field("title")
                                        .value("*" + text + "*")
                                        .boost(1.5f)
                                ))
                                .should(s -> s.wildcard(w -> w
                                        .field("description")
                                        .value("*" + text + "*")
                                ))
                                .should(s -> s.wildcard(w -> w
                                        .field("fileType")
                                        .value("*" + text + "*")
                                ))
                                .filter(f -> f.term(t -> t
                                        .field("status")
                                        .value(1)
                                ))
                                .minimumShouldMatch("1")
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
