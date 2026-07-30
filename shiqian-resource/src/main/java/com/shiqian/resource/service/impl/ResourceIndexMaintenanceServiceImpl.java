package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shiqian.resource.document.ResourceDocument;
import com.shiqian.resource.document.ResourceDocumentMapper;
import com.shiqian.resource.dto.IndexConsistencyVO;
import com.shiqian.resource.entity.Resource;
import com.shiqian.resource.mapper.ResourceMapper;
import com.shiqian.resource.monitoring.ResourceBusinessMetrics;
import com.shiqian.resource.repository.ResourceDocumentRepository;
import com.shiqian.resource.service.AuthorEnrichmentService;
import com.shiqian.resource.service.ResourceIndexMaintenanceService;
import com.shiqian.resource.service.ResourceTaxonomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceIndexMaintenanceServiceImpl
        implements ResourceIndexMaintenanceService {

    private static final int BATCH_SIZE = 200;

    private final ResourceMapper resourceMapper;
    private final ResourceDocumentRepository documentRepository;
    private final ResourceDocumentMapper documentMapper;
    private final ResourceTaxonomyService taxonomyService;
    private final AuthorEnrichmentService authorEnrichmentService;
    private final ResourceBusinessMetrics businessMetrics;

    @Override
    public long rebuildIndex() {
        documentRepository.deleteAll();
        long indexed = 0;
        long current = 1;
        while (true) {
            Page<Resource> page = resourceMapper.selectPage(
                    new Page<>(current, BATCH_SIZE),
                    publishedQuery());
            List<Resource> resources = page.getRecords();
            if (resources.isEmpty()) {
                break;
            }
            taxonomyService.enrich(resources);
            authorEnrichmentService.enrich(resources);
            documentRepository.saveAll(resources.stream()
                    .map(documentMapper::fromResource)
                    .toList());
            indexed += resources.size();
            if (!page.hasNext()) {
                break;
            }
            current++;
        }
        log.info("Elasticsearch 资源索引重建完成: indexed={}", indexed);
        return indexed;
    }

    @Override
    public IndexConsistencyVO checkConsistency() {
        List<Long> mysqlIds = resourceMapper.selectList(publishedQuery()).stream()
                .map(Resource::getId)
                .toList();
        Set<Long> mysqlIdSet = new HashSet<>(mysqlIds);
        Set<Long> documentIds = new HashSet<>();
        Iterable<ResourceDocument> documents = documentRepository.findAll();
        if (documents != null) {
            documents.forEach(document -> documentIds.add(document.getId()));
        }

        List<Long> missing = mysqlIds.stream()
                .filter(id -> !documentIds.contains(id))
                .toList();
        List<Long> orphan = documentIds.stream()
                .filter(id -> !mysqlIdSet.contains(id))
                .sorted()
                .toList();
        return new IndexConsistencyVO(
                mysqlIds.size(),
                documentIds.size(),
                missing,
                orphan);
    }

    @Scheduled(
            fixedDelayString = "${resource.search.consistency-interval-ms:3600000}",
            initialDelayString = "${resource.search.consistency-initial-delay-ms:600000}")
    public void scheduledConsistencyCheck() {
        try {
            IndexConsistencyVO result = checkConsistency();
            if (!result.isConsistent()) {
                log.warn(
                        "MySQL 与 Elasticsearch 资源索引不一致: missing={}, orphan={}",
                        result.getMissingDocumentIds().size(),
                        result.getOrphanDocumentIds().size());
            }
        } catch (Exception exception) {
            businessMetrics.elasticsearchSyncFailed();
            log.error("资源索引一致性检查失败", exception);
        }
    }

    private QueryWrapper<Resource> publishedQuery() {
        return new QueryWrapper<Resource>()
                .eq("deleted", 0)
                .eq("status", 1)
                .orderByAsc("id");
    }
}
