package com.bjak.assistant.retriever;

import com.bjak.assistant.model.Fact;
import com.bjak.assistant.store.FactStore;
import com.bjak.assistant.store.SQLiteIndex;

import java.util.*;

/**
 * Retrieves top-k facts using BM25 scoring with metadata filtering and heuristic intent boosting.
 */
public class BM25Retriever {
    private final FactStore factStore;
    private final SQLiteIndex index;

    public BM25Retriever(FactStore factStore, SQLiteIndex index) {
        this.factStore = factStore;
        this.index = index;
    }

    public static class RetrievalResult {
        private final List<Fact> facts;
        private final boolean confident;
        private final double topScore;

        public RetrievalResult(List<Fact> facts, boolean confident, double topScore) {
            this.facts = facts;
            this.confident = confident;
            this.topScore = topScore;
        }

        public List<Fact> getFacts() { return facts; }
        public boolean isConfident() { return confident; }
        public double getTopScore() { return topScore; }
    }

    public RetrievalResult retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return new RetrievalResult(Collections.emptyList(), false, 0.0);
        }

        List<SQLiteIndex.FactScore> rawScores = index.search(query, topK * 2);
        Map<String, Double> scoreMap = new HashMap<>();

        for (SQLiteIndex.FactScore fs : rawScores) {
            scoreMap.put(fs.fact.getId(), fs.score);
        }

        // Apply metadata filters & intent boosts
        applyMetadataBoosts(query.toLowerCase(), scoreMap);

        // Sort by final boosted scores
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scoreMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Fact> resultFacts = new ArrayList<>();
        double highestScore = sorted.isEmpty() ? 0.0 : sorted.get(0).getValue();

        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            if (entry.getValue() > 0.05) {
                Fact f = factStore.getFact(entry.getKey());
                if (f != null) {
                    resultFacts.add(f);
                }
            }
        }

        // If top score is negligible, flag as not confident
        boolean confident = !resultFacts.isEmpty() && highestScore >= 0.15;
        return new RetrievalResult(resultFacts, confident, highestScore);
    }

    private void applyMetadataBoosts(String query, Map<String, Double> scoreMap) {
        // Boost for current role / Wipro
        if (query.contains("current") || query.contains("present") || query.contains("wipro")) {
            boost(scoreMap, "FACT-001", 3.5);
        }
        // Boost for CabCharge / Taxi app / automotive
        if (query.contains("cabcharge") || query.contains("taxi") || query.contains("white label") || query.contains("mmkv")) {
            boost(scoreMap, "FACT-002", 4.0);
        }
        // Boost for Aviva / Insurance & Wellbeing
        if (query.contains("aviva") || query.contains("insurance") || query.contains("wellbeing") || query.contains("sonarqube")) {
            boost(scoreMap, "FACT-003", 4.0);
        }
        // Boost for Spring Boot / backend / microservices
        if (query.contains("spring boot") || query.contains("eureka") || query.contains("backend") || query.contains("microservice") || query.contains("api gateway")) {
            boost(scoreMap, "FACT-004", 4.0);
        }
        // Boost for ZoomCar / Zoom One
        if (query.contains("zoomcar") || query.contains("zoom one") || query.contains("guest") || query.contains("host") || query.contains("clevertap")) {
            boost(scoreMap, "FACT-005", 3.5);
            boost(scoreMap, "FACT-006", 4.0);
        }
        // Boost for Tekion / OTT TV / Android TV / MQTT
        if (query.contains("tekion") || query.contains("ott") || query.contains("android tv") || query.contains("mqtt") || query.contains("rfid") || query.contains("exoplayer")) {
            boost(scoreMap, "FACT-007", 3.5);
            boost(scoreMap, "FACT-008", 4.0);
        }
        // Boost for Zibal Tech / StrataGem / N-Radius / SmartCat
        if (query.contains("zibal") || query.contains("stratagem") || query.contains("n-radius") || query.contains("smartcat") || query.contains("cmmi")) {
            boost(scoreMap, "FACT-009", 4.0);
        }
        // Boost for Quarks / AdviceFit
        if (query.contains("quarks") || query.contains("advicefit") || query.contains("housing society")) {
            boost(scoreMap, "FACT-010", 4.0);
        }
        // Boost for Triazine / Domino's / Hello Tax / Ticket-Pro / Traffic-Pro / Aadhaar
        if (query.contains("triazine") || query.contains("domino") || query.contains("hello tax") || query.contains("ticket-pro") || query.contains("traffic-pro") || query.contains("aadhaar")) {
            boost(scoreMap, "FACT-011", 4.0);
        }
        // Boost for SpearHead
        if (query.contains("spearhead") || query.contains("early career")) {
            boost(scoreMap, "FACT-012", 4.0);
        }
        // Boost for Offline AI / Ollama / Qwen / N8n / Local LLMs
        if (query.contains("ai") || query.contains("ollama") || query.contains("qwen") || query.contains("n8n") || query.contains("local llm") || query.contains("offline")) {
            boost(scoreMap, "FACT-013", 4.0);
        }
        // Boost for Team leadership / 22 engineers / experience
        if (query.contains("team") || query.contains("lead") || query.contains("engineers") || query.contains("experience") || query.contains("13+")) {
            boost(scoreMap, "FACT-014", 3.5);
            boost(scoreMap, "FACT-021", 3.0);
        }
        // Boost for Mobile platforms & architecture
        if (query.contains("mobile platform") || query.contains("architectures") || query.contains("clean architecture") || query.contains("mvvm")) {
            boost(scoreMap, "FACT-021", 4.5);
        }
        // Boost for Education / B.Tech IT
        if (query.contains("education") || query.contains("degree") || query.contains("b.tech") || query.contains("btech") || query.contains("university") || query.contains("college")) {
            boost(scoreMap, "FACT-015", 4.0);
        }
        // Boost for IoT / IIT Delhi Certification
        if (query.contains("certification") || query.contains("certified") || query.contains("iit") || query.contains("iot") || query.contains("internet of things")) {
            boost(scoreMap, "FACT-016", 4.0);
        }
        // Boost for Published Apps / Play Store / App Store
        if (query.contains("published") || query.contains("play store") || query.contains("app store") || query.contains("apps")) {
            boost(scoreMap, "FACT-017", 4.0);
        }
        // Boost for Contact / Location / Email / Phone / Bangalore
        if (query.contains("contact") || query.contains("email") || query.contains("phone") || query.contains("location") || query.contains("bangalore") || query.contains("varthur") || query.contains("linkedin") || query.contains("github")) {
            boost(scoreMap, "FACT-018", 4.0);
        }
        // Boost for Transition / timeline
        if (query.contains("transition") || query.contains("timeline") || query.contains("zoomcar to wipro")) {
            boost(scoreMap, "FACT-019", 4.0);
        }
    }

    private void boost(Map<String, Double> scoreMap, String factId, double delta) {
        if (factStore.getFact(factId) != null) {
            scoreMap.put(factId, scoreMap.getOrDefault(factId, 0.0) + delta);
        }
    }
}
