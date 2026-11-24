package com.bjak.assistant.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an atomic verified fact with source provenance and metadata.
 */
public class Fact {
    private String id;
    private String category;
    private String title;
    private String content;
    private String source;
    private List<String> keywords = new ArrayList<>();
    private Map<String, String> metadata = new HashMap<>();

    public Fact() {}

    public Fact(String id, String category, String title, String content, String source, List<String> keywords, Map<String, String> metadata) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.content = content;
        this.source = source;
        if (keywords != null) this.keywords = keywords;
        if (metadata != null) this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return "[" + id + "] " + title + " (" + source + "): " + content;
    }
}
