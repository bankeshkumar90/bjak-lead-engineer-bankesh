package com.bjak.assistant.store;

import com.bjak.assistant.model.Allowlist;
import com.bjak.assistant.model.Fact;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads, parses, and provides in-memory access to facts.yaml and allowlist.json.
 * Designed to be zero-external-dependency for 100% plug-and-play execution.
 */
public class FactStore {
    private final List<Fact> facts = new ArrayList<>();
    private final Map<String, Fact> factById = new HashMap<>();
    private final Allowlist allowlist = new Allowlist();

    public FactStore() {}

    public static FactStore loadDefault() throws IOException {
        FactStore store = new FactStore();
        File factsFile = findFile("data/facts.yaml");
        File allowlistFile = findFile("data/allowlist.json");

        if (factsFile != null && factsFile.exists()) {
            store.loadFactsFromYaml(factsFile.toPath());
        } else {
            throw new IOException("Could not locate data/facts.yaml");
        }

        if (allowlistFile != null && allowlistFile.exists()) {
            store.loadAllowlistFromJson(allowlistFile.toPath());
        }

        return store;
    }

    private static File findFile(String relativePath) {
        File f1 = new File(relativePath);
        if (f1.exists()) return f1;
        File f2 = new File("../" + relativePath);
        if (f2.exists()) return f2;
        String userDir = System.getProperty("user.dir", ".");
        File f3 = new File(userDir, relativePath);
        if (f3.exists()) return f3;
        return null;
    }

    public void loadFactsFromYaml(Path path) throws IOException {
        facts.clear();
        factById.clear();

        List<String> lines = Files.readAllLines(path);
        Fact currentFact = null;
        boolean inKeywords = false;
        boolean inMetadata = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("- id:") || (line.startsWith("id:") && currentFact == null)) {
                if (currentFact != null && currentFact.getId() != null) {
                    facts.add(currentFact);
                    factById.put(currentFact.getId(), currentFact);
                }
                currentFact = new Fact();
                inKeywords = false;
                inMetadata = false;
                String idVal = extractValue(line, "id:");
                currentFact.setId(idVal);
            } else if (currentFact != null) {
                if (line.startsWith("category:")) {
                    currentFact.setCategory(extractValue(line, "category:"));
                    inKeywords = false;
                    inMetadata = false;
                } else if (line.startsWith("title:")) {
                    currentFact.setTitle(extractValue(line, "title:"));
                    inKeywords = false;
                    inMetadata = false;
                } else if (line.startsWith("content:")) {
                    currentFact.setContent(extractValue(line, "content:"));
                    inKeywords = false;
                    inMetadata = false;
                } else if (line.startsWith("source:")) {
                    currentFact.setSource(extractValue(line, "source:"));
                    inKeywords = false;
                    inMetadata = false;
                } else if (line.startsWith("keywords:")) {
                    inKeywords = true;
                    inMetadata = false;
                    String val = extractValue(line, "keywords:");
                    if (val.startsWith("[") && val.endsWith("]")) {
                        parseInlineList(val, currentFact.getKeywords());
                        inKeywords = false;
                    }
                } else if (line.startsWith("metadata:")) {
                    inMetadata = true;
                    inKeywords = false;
                } else if (inKeywords && line.startsWith("-")) {
                    currentFact.getKeywords().add(cleanQuote(line.substring(1).trim()));
                } else if (inMetadata && line.contains(":")) {
                    int colonIdx = line.indexOf(':');
                    String k = line.substring(0, colonIdx).trim();
                    String v = cleanQuote(line.substring(colonIdx + 1).trim());
                    currentFact.getMetadata().put(k, v);
                }
            }
        }
        if (currentFact != null && currentFact.getId() != null) {
            facts.add(currentFact);
            factById.put(currentFact.getId(), currentFact);
        }
    }

    private void parseInlineList(String inline, List<String> target) {
        String content = inline.substring(1, inline.length() - 1);
        String[] parts = content.split(",");
        for (String p : parts) {
            String item = cleanQuote(p.trim());
            if (!item.isEmpty()) target.add(item);
        }
    }

    private String extractValue(String line, String key) {
        int idx = line.indexOf(key);
        if (idx == -1) return "";
        String val = line.substring(idx + key.length()).trim();
        return cleanQuote(val);
    }

    private String cleanQuote(String s) {
        s = s.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length() >= 2) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    public void loadAllowlistFromJson(Path path) throws IOException {
        String json = Files.readString(path);
        allowlist.setEmployers(extractJsonArray(json, "employers"));
        allowlist.setTitles(extractJsonArray(json, "titles"));
        allowlist.setTechnologies(extractJsonArray(json, "technologies"));
        allowlist.setYears(extractJsonArray(json, "years"));
    }

    private List<String> extractJsonArray(String json, String fieldName) {
        List<String> list = new ArrayList<>();
        int keyPos = json.indexOf("\"" + fieldName + "\"");
        if (keyPos == -1) return list;
        int bracketStart = json.indexOf('[', keyPos);
        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketStart != -1 && bracketEnd != -1) {
            String sub = json.substring(bracketStart + 1, bracketEnd);
            String[] tokens = sub.split(",");
            for (String t : tokens) {
                String clean = cleanQuote(t.trim());
                if (!clean.isEmpty()) list.add(clean);
            }
        }
        return list;
    }

    public List<Fact> getAllFacts() { return facts; }
    public Fact getFact(String id) { return factById.get(id); }
    public Allowlist getAllowlist() { return allowlist; }
    public int size() { return facts.size(); }
}
