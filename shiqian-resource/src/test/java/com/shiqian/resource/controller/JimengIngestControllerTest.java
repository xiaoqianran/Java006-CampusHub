package com.shiqian.resource.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiqian.resource.dto.JimengBatchRequest;
import com.shiqian.resource.dto.JimengPromptItem;
import com.shiqian.resource.service.JimengIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JimengIngestControllerTest {

    private static final String TOKEN = "test-token-at-least-32-characters";

    private JimengIngestService ingestService;
    private JimengIngestController controller;

    @BeforeEach
    void setUp() {
        ingestService = mock(JimengIngestService.class);
        controller = new JimengIngestController(ingestService, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "ingestToken", TOKEN);
    }

    @Test
    void acceptsLoopbackRequestWithMatchingToken() {
        when(ingestService.ingestBatch(anyList())).thenReturn(Map.of("ok", true));

        Map<String, Object> result = controller.batch(authorizedRequest("127.0.0.1"), new JimengBatchRequest());

        assertEquals(true, result.get("ok"));
    }

    @Test
    void rejectsWrongTokenEvenFromLoopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Jimeng-Sync-Token", "wrong-token");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(request, new JimengBatchRequest()));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rejectsNonLocalRequestEvenWithMatchingToken() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(authorizedRequest("203.0.113.10"), new JimengBatchRequest()));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rejectsLoopbackPeerWhenForwardedHeadersPresent() {
        MockHttpServletRequest request = authorizedRequest("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(request, new JimengBatchRequest()));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void rejectsLoopbackPeerWhenXRealIpPresent() {
        MockHttpServletRequest request = authorizedRequest("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.10");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(request, new JimengBatchRequest()));

        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void remainsClosedWhenServerTokenIsMissing() {
        ReflectionTestUtils.setField(controller, "ingestToken", "too-short");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(authorizedRequest("127.0.0.1"), new JimengBatchRequest()));

        assertEquals(503, error.getStatusCode().value());
    }

    @Test
    void rejectsOversizedBatch() {
        JimengBatchRequest body = new JimengBatchRequest();
        body.setItems(Collections.nCopies(2001, new JimengPromptItem()));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.batch(authorizedRequest("127.0.0.1"), body));

        assertEquals(413, error.getStatusCode().value());
    }

    private MockHttpServletRequest authorizedRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Jimeng-Sync-Token", TOKEN);
        return request;
    }
}
