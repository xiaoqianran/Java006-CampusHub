package com.shiqian.common.content;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 DFA 的敏感词过滤器。
 */
public class SensitiveWordFilter {

    private final Node root = new Node();

    public SensitiveWordFilter(Collection<String> words) {
        reload(words);
    }

    public final void reload(Collection<String> words) {
        root.children.clear();
        root.end = false;
        if (words == null || words.isEmpty()) {
            return;
        }
        for (String word : words) {
            addWord(word);
        }
    }

    public boolean contains(String text) {
        return !findAll(text).isEmpty();
    }

    public Set<String> findAll(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> words = new LinkedHashSet<>();
        String normalized = normalize(text);
        for (int i = 0; i < normalized.length(); i++) {
            Node current = root;
            StringBuilder matched = new StringBuilder();
            for (int j = i; j < normalized.length(); j++) {
                current = current.children.get(normalized.charAt(j));
                if (current == null) {
                    break;
                }
                matched.append(normalized.charAt(j));
                if (current.end) {
                    words.add(matched.toString());
                }
            }
        }
        return words;
    }

    public String replace(String text, char replacement) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (String word : findAll(text)) {
            result = result.replaceAll("(?iu)" + java.util.regex.Pattern.quote(word),
                    String.valueOf(replacement).repeat(word.length()));
        }
        return result;
    }

    private void addWord(String word) {
        String normalized = normalize(word).trim();
        if (normalized.isEmpty()) {
            return;
        }
        Node current = root;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current = current.children.computeIfAbsent(c, key -> new Node());
        }
        current.end = true;
    }

    private String normalize(String text) {
        return text.toLowerCase();
    }

    private static class Node {

        private final Map<Character, Node> children = new ConcurrentHashMap<>();

        private boolean end;
    }
}
