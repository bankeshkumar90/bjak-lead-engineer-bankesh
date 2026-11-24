package com.bjak.assistant.generator;

import com.bjak.assistant.model.Claim;
import com.bjak.assistant.model.Fact;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Generator that invokes a local or remote model (Ollama / Qwen / OpenAI compatible)
 * requesting structured JSON answers citing explicit Fact IDs.
 * Automatically fails fast on timeout/unavailability to delegate to deterministic Fallback.
 */
public class LLMGenerator {
    private final String endpoint;
    private final String modelName;
    private final int timeoutMs;
    private final HttpClient httpClient;

    public LLMGenerator() {
        this(
            System.getenv().getOrDefault("OLLAMA_HOST", "http://localhost:11434"),
            System.getenv().getOrDefault("MODEL_NAME", "qwen2.5:7b"),
            Integer.parseInt(System.getenv().getOrDefault("LLM_TIMEOUT_MS", "4000"))
        );
    }

    public LLMGenerator(String endpoint, String modelName, int timeoutMs) {
        this.endpoint = endpoint;
        this.modelName = modelName;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)))
                .build();
    }

    public static class LLMResult {
        public final String answer;
        public final List<Claim> claims;

        public LLMResult(String answer, List<Claim> claims) {
            this.answer = answer;
            this.claims = claims;
        }
    }

    public LLMResult generateWithRetry(String query, List<Fact> facts, String previousViolation) throws Exception {
        String prompt = buildPrompt(query, facts, previousViolation);
        String requestJson = buildOllamaRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("LLM API returned status " + response.statusCode() + ": " + response.body());
        }

        return parseOllamaResponse(response.body());
    }

    private String buildPrompt(String query, List<Fact> facts, String previousViolation) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an AI Career Assistant representing Bankesh Kumar (Lead Engineer / Engineering Manager) for technical interviews.\n");
        sb.append("CRITICAL INSTRUCTIONS:\n");
        sb.append("1. Answer the candidate question using ONLY the provided facts below.\n");
        sb.append("2. NEVER invent, exaggerate, or fabricate employers, qualifications, metrics, or technologies.\n");
        sb.append("3. Every factual statement MUST cite an exact Fact ID from the provided list in brackets (e.g. [FACT-001]).\n");
        sb.append("4. Return your output strictly as a JSON object with keys 'answer' (string) and 'claims' (list of {text, fact_ids}).\n\n");

        if (previousViolation != null) {
            sb.append("ATTENTION - PREVIOUS GENERATION FAILED VERIFICATION:\n");
            sb.append(previousViolation).append("\nCorrect these errors immediately.\n\n");
        }

        sb.append("VERIFIED FACTS:\n");
        for (Fact f : facts) {
            sb.append("- [").append(f.getId()).append("] ").append(f.getTitle()).append(": ").append(f.getContent()).append("\n");
        }

        sb.append("\nQUESTION: ").append(query).append("\n");
        sb.append("\nJSON RESPONSE:\n");
        return sb.toString();
    }

    private String buildOllamaRequestBody(String prompt) {
        String escapedPrompt = escapeJson(prompt);
        return String.format(
            "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false, \"format\": \"json\", \"options\": {\"temperature\": 0.1}}",
            modelName, escapedPrompt
        );
    }

    private LLMResult parseOllamaResponse(String responseJson) throws Exception {
        // Extract "response" string from Ollama wrapper JSON
        int respIdx = responseJson.indexOf("\"response\":");
        if (respIdx == -1) {
            throw new IllegalArgumentException("Invalid Ollama response: missing 'response' field");
        }
        int quoteStart = responseJson.indexOf('"', respIdx + 11);
        int quoteEnd = findClosingQuote(responseJson, quoteStart);
        String innerJson = unescapeJson(responseJson.substring(quoteStart + 1, quoteEnd));

        // Parse inner JSON fields "answer" and "claims"
        String answer = "";
        int ansIdx = innerJson.indexOf("\"answer\":");
        if (ansIdx != -1) {
            int aStart = innerJson.indexOf('"', ansIdx + 9);
            int aEnd = findClosingQuote(innerJson, aStart);
            answer = unescapeJson(innerJson.substring(aStart + 1, aEnd));
        }

        List<Claim> claims = new ArrayList<>();
        // Simple extraction of fact IDs from answer or inner JSON
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("FACT-\\d{3}").matcher(innerJson);
        List<String> foundIds = new ArrayList<>();
        while (m.find()) {
            String fid = m.group();
            if (!foundIds.contains(fid)) foundIds.add(fid);
        }
        claims.add(new Claim(answer, foundIds));

        return new LLMResult(answer, claims);
    }

    private int findClosingQuote(String s, int openQuote) {
        for (int i = openQuote + 1; i < s.length(); i++) {
            if (s.charAt(i) == '"' && s.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return s.length() - 1;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\t", "\t")
                .replace("\\r", "\r");
    }
}
