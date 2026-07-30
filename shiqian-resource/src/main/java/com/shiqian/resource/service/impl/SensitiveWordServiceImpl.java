package com.shiqian.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.shiqian.common.content.SensitiveWordFilter;
import com.shiqian.common.exception.BusinessException;
import com.shiqian.resource.dto.SensitiveWordDTO;
import com.shiqian.resource.entity.SensitiveWord;
import com.shiqian.resource.mapper.SensitiveWordMapper;
import com.shiqian.resource.service.SensitiveWordService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper mapper;
    private final SensitiveWordFilter filter;

    @Value("${content.sensitive-words:违规,敏感词,广告}")
    private String fallbackWords;

    @PostConstruct
    @Transactional
    public void initialize() {
        if (mapper.selectCount(new QueryWrapper<SensitiveWord>().eq("deleted", 0)) == 0) {
            for (String word : fallbackList()) {
                SensitiveWord item = new SensitiveWord();
                item.setWord(word);
                item.setLevel(2);
                item.setStatus(1);
                item.setCreatedBy(0L);
                item.setDeleted(0);
                item.setCreateTime(LocalDateTime.now());
                item.setUpdateTime(LocalDateTime.now());
                mapper.insert(item);
            }
        }
        reload();
    }

    @Override
    public List<SensitiveWord> list(String keyword) {
        return mapper.selectList(new QueryWrapper<SensitiveWord>()
                .eq("deleted", 0)
                .like(StringUtils.hasText(keyword), "word", keyword)
                .orderByDesc("level")
                .orderByAsc("word"));
    }

    @Override
    @Transactional
    public Long create(SensitiveWordDTO dto, Long operatorId) {
        String word = dto.getWord().trim();
        SensitiveWord existing = mapper.selectOne(
                new QueryWrapper<SensitiveWord>().eq("word", word));
        if (existing != null) {
            if (existing.getDeleted() == 0) {
                throw new BusinessException("敏感词已存在");
            }
            apply(existing, dto);
            existing.setDeleted(0);
            existing.setCreatedBy(operatorId);
            existing.setUpdateTime(LocalDateTime.now());
            mapper.updateById(existing);
            reload();
            return existing.getId();
        }
        SensitiveWord item = new SensitiveWord();
        apply(item, dto);
        item.setCreatedBy(operatorId);
        item.setDeleted(0);
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        mapper.insert(item);
        reload();
        return item.getId();
    }

    @Override
    @Transactional
    public void update(Long id, SensitiveWordDTO dto) {
        SensitiveWord item = require(id);
        if (mapper.selectCount(new QueryWrapper<SensitiveWord>()
                .eq("word", dto.getWord().trim())
                .eq("deleted", 0)
                .ne("id", id)) > 0) {
            throw new BusinessException("敏感词已存在");
        }
        apply(item, dto);
        item.setUpdateTime(LocalDateTime.now());
        mapper.updateById(item);
        reload();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SensitiveWord item = require(id);
        item.setDeleted(1);
        item.setUpdateTime(LocalDateTime.now());
        mapper.updateById(item);
        reload();
    }

    @Override
    public synchronized void reload() {
        filter.reload(mapper.selectList(new QueryWrapper<SensitiveWord>()
                        .eq("deleted", 0)
                        .eq("status", 1))
                .stream()
                .map(SensitiveWord::getWord)
                .toList());
    }

    private SensitiveWord require(Long id) {
        SensitiveWord item = mapper.selectById(id);
        if (item == null || item.getDeleted() == 1) {
            throw new BusinessException("敏感词不存在");
        }
        return item;
    }

    private void apply(SensitiveWord item, SensitiveWordDTO dto) {
        item.setWord(dto.getWord().trim());
        item.setLevel(dto.getLevel() == null ? 2 : dto.getLevel());
        item.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private List<String> fallbackList() {
        return Arrays.stream(fallbackWords.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
