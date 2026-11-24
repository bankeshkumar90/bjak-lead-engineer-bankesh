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

        answerBuilder.append("Based on Bankesh Kumar's verified professional documentation:\n\n");

        for (int i = 0; i < retrievedFacts.size(); i++) {
            Fact f = retrievedFacts.get(i);
            answerBuilder.append("• ").append(f.getContent())
                         .append(" [").append(f.getId()).append("]\n");

            citedIds.add(f.getId());
            sources.add("[" + f.getId() + "] " + f.getTitle() + " (Source: " + f.getSource() + ")");
        }

        result.setStatus(QueryResult.Status.SUCCESS);
        result.setExecutionMode("FALLBACK_EXTRACTIVE");
        result.setAnswer(answerBuilder.toString().trim());
        result.setCitedFactIds(citedIds);
        result.setSourceReferences(sources);
        result.addAudit("Fallback: Generated extractive grounded answer from " + retrievedFacts.size() + " verified facts.");

        return result;
    }
}
