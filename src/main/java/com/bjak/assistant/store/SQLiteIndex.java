package com.bjak.assistant.store;

import com.bjak.assistant.model.Fact;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implements the hybrid fact index:
 * 1. Primary: SQLite FTS5 index on disk (facts.db) with native bm25() ranking.
 * 2. Embedded: In-memory BM25 engine (k1=1.2, b=0.75) ensuring zero-failure resilience
 *    even when external sqlite3 execution is restricted or unavailable.
 */
public class SQLiteIndex {
    private final String dbPath;
    private final List<Fact> indexedFacts = new ArrayList<>();
    private final Map<String, Fact> factMap = new HashMap<>();

    // In-memory BM25 state
    private final Map<String, Integer> docLengths = new HashMap<>();
    private double avgDocLength = 0;
    private final Map<String, Map<String, Integer>> docTermFreqs = new HashMap<>();
    private final Map<String, Integer> termDocFreqs = new HashMap<>();
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public SQLiteIndex() {
        this("facts.db");
    }

    public SQLiteIndex(String dbPath) {
        this.dbPath = dbPath;
    }

    public synchronized void indexFacts(List<Fact> facts) {
        indexedFacts.clear();
        factMap.clear();
        docLengths.clear();
        docTermFreqs.clear();
        termDocFreqs.clear();

        indexedFacts.addAll(facts);
        for (Fact f : facts) {
            factMap.put(f.getId(), f);
        }

        buildInMemoryBM25(facts);
        buildSQLiteFTS5(facts);
    }

    private void buildInMemoryBM25(List<Fact> facts) {
        long totalLength = 0;
        for (Fact f : facts) {
            String fullText = f.getTitle() + " " + f.getContent() + " " + String.join(" ", f.getKeywords());
            List<String> tokens = tokenize(fullText);
            docLengths.put(f.getId(), tokens.size());
            totalLength += tokens.size();

            Map<String, Integer> tf = new HashMap<>();
            Set<String> uniqueTerms = new HashSet<>();
            for (String term : tokens) {
                tf.put(term, tf.getOrDefault(term, 0) + 1);
                uniqueTerms.add(term);
            }
            docTermFreqs.put(f.getId(), tf);
            for (String term : uniqueTerms) {
                termDocFreqs.put(term, termDocFreqs.getOrDefault(term, 0) + 1);
            }
        }
        avgDocLength = facts.isEmpty() ? 0 : (double) totalLength / facts.size();
    }

    private void buildSQLiteFTS5(List<Fact> facts) {
        try {
            File sqlScript = File.createTempFile("build_fts5_", ".sql");
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(sqlScript), StandardCharsets.UTF_8))) {
                pw.println("DROP TABLE IF EXISTS facts_fts;");
                pw.println("CREATE VIRTUAL TABLE facts_fts USING fts5(id UNINDEXED, title, content, category, keywords, source);");
                for (Fact f : facts) {
                    String id = escapeSql(f.getId());
                    String title = escapeSql(f.getTitle());
                    String content = escapeSql(f.getContent());
                    String cat = escapeSql(f.getCategory());
                    String kw = escapeSql(String.join(" ", f.getKeywords()));
                    String src = escapeSql(f.getSource());
                    pw.printf("INSERT INTO facts_fts(id, title, content, category, keywords, source) VALUES ('%s', '%s', '%s', '%s', '%s', '%s');%n",
                            id, title, content, cat, kw, src);
                }
            }

            ProcessBuilder pb = new ProcessBuilder("sqlite3", dbPath, ".read " + sqlScript.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor();
            sqlScript.delete();
        } catch (Exception e) {
            // Fallback to in-memory BM25 seamlessly
        }
    }

    public List<FactScore> search(String query, int topK) {
        // Try SQLite FTS5 search first
        List<FactScore> ftsResults = searchFTS5(query, topK);
        if (ftsResults != null && !ftsResults.isEmpty()) {
            return ftsResults;
        }

        // Fallback to in-memory BM25
        return searchInMemoryBM25(query, topK);
    }

    private List<FactScore> searchFTS5(String query, int topK) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return Collections.emptyList();

        // Build FTS5 match query with OR logic
        StringBuilder matchBuilder = new StringBuilder();
        for (int i = 0; i < queryTokens.size(); i++) {
            if (i > 0) matchBuilder.append(" OR ");
            matchBuilder.append(queryTokens.get(i).replace("\"", ""));
        }
        String ftsQuery = matchBuilder.toString();

        List<FactScore> results = new ArrayList<>();
        try {
            String sql = String.format("SELECT id, bm25(facts_fts) as score FROM facts_fts WHERE facts_fts MATCH '%s' ORDER BY score ASC LIMIT %d;",
                    escapeSql(ftsQuery), topK);
            ProcessBuilder pb = new ProcessBuilder("sqlite3", dbPath, sql);
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        String id = parts[0].trim();
                        double score = Double.parseDouble(parts[1].trim());
                        Fact f = factMap.get(id);
                        if (f != null) {
                            // FTS5 bm25 returns negative scores where more negative = better
                            double normalizedScore = Math.abs(score);
                            results.add(new FactScore(f, normalizedScore));
                        }
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return results;
    }

    public List<FactScore> searchInMemoryBM25(String query, int topK) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return Collections.emptyList();

        int N = indexedFacts.size();
        List<FactScore> scores = new ArrayList<>();

        for (Fact fact : indexedFacts) {
            String docId = fact.getId();
            int docLen = docLengths.getOrDefault(docId, 0);
            Map<String, Integer> tfMap = docTermFreqs.getOrDefault(docId, Collections.emptyMap());

            double docScore = 0.0;
            for (String term : queryTokens) {
                int tf = tfMap.getOrDefault(term, 0);
                int df = termDocFreqs.getOrDefault(term, 0);
                if (df == 0) continue;

                // Standard Lucene/BM25 IDF: log(1 + (N - df + 0.5) / (df + 0.5))
                double idf = Math.log(1.0 + (N - df + 0.5) / (df + 0.5));
                double tfPart = (tf * (K1 + 1.0)) / (tf + K1 * (1.0 - B + B * (docLen / Math.max(1.0, avgDocLength))));
                docScore += idf * tfPart;
            }

            // Keyword boost for explicit term match in title or keywords
            String lowerQ = query.toLowerCase();
            for (String kw : fact.getKeywords()) {
                if (lowerQ.contains(kw.toLowerCase())) {
                    docScore += 1.5;
                }
            }
            if (lowerQ.contains(fact.getTitle().toLowerCase())) {
                docScore += 2.0;
            }

            if (docScore > 0.05) {
                scores.add(new FactScore(fact, docScore));
            }
        }

        scores.sort((a, b) -> Double.compare(b.score, a.score));
        if (scores.size() > topK) {
            return scores.subList(0, topK);
        }
        return scores;
    }

    public List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        String[] raw = text.toLowerCase().replaceAll("[^a-z0-9_\\-\\s]", " ").split("\\s+");
        List<String> tokens = new ArrayList<>();
        Set<String> stopWords = Set.of("the", "a", "an", "is", "in", "at", "of", "on", "and", "or", "to", "for", "with", "what", "how", "did", "was", "are", "does");
        for (String s : raw) {
            s = s.trim();
            if (s.length() > 1 && !stopWords.contains(s)) {
                tokens.add(s);
            }
        }
        return tokens;
    }

    private String escapeSql(String val) {
        if (val == null) return "";
        return val.replace("'", "''");
    }

    public static class FactScore {
        public final Fact fact;
        public final double score;

        public FactScore(Fact fact, double score) {
            this.fact = fact;
            this.score = score;
        }
    }
}
