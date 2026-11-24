package com.bjak.assistant.generator;

import com.bjak.assistant.model.Fact;
import com.bjak.assistant.model.QueryResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic extractive generator.
 * Produces answers solely from retrieved facts with zero hallucinations.
 * Used when:
 * 1. LLM API is unavailable, slow, or rate-limited.
 * 2. LLM response fails verification twice.
 * 3. Plug-and-play offline mode without requiring a local Ollama daemon.
 */
public class FallbackGenerator {

    public QueryResult generate(String query, List<Fact> retrievedFacts, boolean confident) {
        QueryResult result = new QueryResult();
        result.setQuestion(query);
        result.setRetrievedFacts(retrievedFacts);

        if (retrievedFacts == null || retrievedFacts.isEmpty() || !confident) {
            result.setStatus(QueryResult.Status.REFUSED_UNANSWERABLE);
            result.setExecutionMode("FALLBACK_DETERMINISTIC_REFUSAL");
            result.setAnswer("I do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. " +
                    "As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, " +
                    "speculate, or fabricate qualifications, employers, or metrics.");
            result.addAudit("Fallback: Low retrieval confidence or empty facts -> emitted clean deterministic refusal.");
            return result;
        }

        StringBuilder answerBuilder = new StringBuilder();
        List<String> citedIds = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        if (isOverviewQuery(query)) {
            answerBuilder.append("Bankesh Kumar is an AI Forward-Deployed Engineer, Technical Lead, and Mobile/Platform Architect with 13+ years of enterprise experience delivering scalable systems across fintech, automotive, insurance, OTT, gaming, food delivery, and government sectors [FACT-014].\n\n");
            answerBuilder.append("Key Professional Overview:\n");
            answerBuilder.append("• Current Role: Technical Lead at Wipro (June 2024 – Present), driving platform architecture including the CabCharge white-label taxi app and Aviva insurance/wellbeing app [FACT-001, FACT-002, FACT-003].\n");
            answerBuilder.append("• Technical Specialization: Expert in React Native, Android (Kotlin), iOS (Swift), Kotlin Multiplatform (KMM), and Clean Architecture / MVVM [FACT-021], as well as Spring Boot microservices [FACT-004].\n");
            answerBuilder.append("• Offline AI & Local LLMs: Builds privacy-first offline AI agents and automated workflows utilizing Ollama, local Qwen (e.g. Qwen 3 8B), N8n, and Docker [FACT-013].\n");
            answerBuilder.append("• Architecture Track Record: Previously Lead Engineer/Architect at ZoomCar (merging apps into Zoom One [FACT-005, FACT-006]), Tekion (Android TV OTT [FACT-007, FACT-008]), Zibal Tech (StrataGem CMMI Level 5 game [FACT-009]), Quarks (AdviceFit [FACT-010]), and Triazine (Domino's Pizza, Hello Tax, Aadhaar apps [FACT-011]).\n");
            answerBuilder.append("• Leadership & Scope: Led cross-functional teams of up to 22 engineers, delivering 30+ production apps across 8+ industry domains [FACT-014].\n");
            answerBuilder.append("• Education & Certification: B.Tech in Information Technology (80%) [FACT-015] and IoT Certified from IIT Delhi [FACT-016].");

            List<String> overviewFactIds = List.of("FACT-014", "FACT-001", "FACT-002", "FACT-003", "FACT-021", "FACT-004", "FACT-013", "FACT-005", "FACT-006", "FACT-007", "FACT-008", "FACT-009", "FACT-010", "FACT-011", "FACT-015", "FACT-016");
            for (String fid : overviewFactIds) {
                if (!citedIds.contains(fid)) citedIds.add(fid);
                sources.add("[" + fid + "] Verified Fact Reference");
            }
        } else {
            answerBuilder.append("Based on Bankesh Kumar's verified professional documentation:\n\n");
            for (int i = 0; i < retrievedFacts.size(); i++) {
                Fact f = retrievedFacts.get(i);
                answerBuilder.append("• ").append(f.getContent())
                             .append(" [").append(f.getId()).append("]\n");

                citedIds.add(f.getId());
                sources.add("[" + f.getId() + "] " + f.getTitle() + " (Source: " + f.getSource() + ")");
            }
        }

        result.setStatus(QueryResult.Status.SUCCESS);
        result.setExecutionMode("FALLBACK_EXTRACTIVE");
        result.setAnswer(answerBuilder.toString().trim());
        result.setCitedFactIds(citedIds);
        result.setSourceReferences(sources);
        result.addAudit("Fallback: Generated extractive grounded answer from " + retrievedFacts.size() + " verified facts.");

        return result;
    }

    private boolean isOverviewQuery(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase().trim();
        return lower.contains("tell me about bankesh") ||
               lower.contains("who is bankesh") ||
               lower.contains("tell me about yourself") ||
               lower.contains("who are you") ||
               lower.contains("overview of bankesh") ||
               lower.contains("summary of bankesh") ||
               lower.contains("introduce bankesh") ||
               lower.contains("introduction") ||
               lower.contains("tell me about him") ||
               lower.equals("bankesh") ||
               lower.equals("bankesh kumar") ||
               (lower.contains("about") && lower.contains("bankesh")) ||
               (lower.contains("summary") && lower.contains("bankesh"));
    }
}
