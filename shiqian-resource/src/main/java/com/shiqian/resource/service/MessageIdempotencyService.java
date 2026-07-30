package com.shiqian.resource.service;

import com.shiqian.resource.mapper.ConsumedMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageIdempotencyService {

    private final ConsumedMessageMapper consumedMessageMapper;

    public boolean isConsumed(String messageId, String consumerName) {
        validate(messageId, consumerName);
        return consumedMessageMapper.count(messageId, consumerName) > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markConsumed(String messageId, String consumerName) {
        validate(messageId, consumerName);
        return consumedMessageMapper.insertIgnore(messageId, consumerName) == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryStartWithinTransaction(String messageId, String consumerName) {
        validate(messageId, consumerName);
        return consumedMessageMapper.insertIgnore(messageId, consumerName) == 1;
    }

    private void validate(String messageId, String consumerName) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(consumerName)) {
            throw new IllegalArgumentException("messageId and consumerName must not be blank");
        }
    }
}
