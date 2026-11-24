package com.bjak.assistant.pipeline;

import com.bjak.assistant.generator.FallbackGenerator;
import com.bjak.assistant.generator.LLMGenerator;
import com.bjak.assistant.model.Fact;
import com.bjak.assistant.model.QueryResult;
import com.bjak.assistant.model.VerificationResult;
import com.bjak.assistant.retriever.BM25Retriever;
import com.bjak.assistant.router.QueryRouter;
import com.bjak.assistant.store.FactStore;
import com.bjak.assistant.store.SQLiteIndex;
import com.bjak.assistant.verifier.ClaimVerifier;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end orchestration pipeline implementing the Career Assistant architecture:
 * CLI/Harness -> Router -> Retriever -> Generator (Ollama) -> Verifier (Retry x1) -> Fallback -> Output.
 */
public class CareerAssistantPipeline {
    private final FactStore factStore;
    private final SQLiteIndex index;
    private final QueryRouter router;
    private final BM25Retriever retriever;
    private final LLMGenerator generator;
    private final ClaimVerifier verifier;
    private final FallbackGenerator fallback;

    private boolean enableLLM = true;

    public CareerAssistantPipeline(FactStore factStore, SQLiteIndex index) {
        this.factStore = factStore;
        this.index = index;
        this.router = new QueryRouter();
        this.retriever = new BM25Retriever(factStore, index);
        this.generator = new LLMGenerator();
        this.verifier = new ClaimVerifier(factStore.getAllowlist());
        this.fallback = new FallbackGenerator();

        // Check if LLM is explicitly disabled via env
        String disableLlmEnv = System.getenv("DISABLE_LLM");
        if ("true".equalsIgnoreCase(disableLlmEnv) || "1".equals(disableLlmEnv)) {
            this.enableLLM = false;
        }
    }

    public static CareerAssistantPipeline createDefault() throws Exception {
        FactStore store = FactStore.loadDefault();
        SQLiteIndex index = new SQLiteIndex();
        index.indexFacts(store.getAllFacts());
        return new CareerAssistantPipeline(store, index);
    }

    public void setEnableLLM(boolean enableLLM) {
        this.enableLLM = enableLLM;
    }

    public QueryResult process(String query) {
        long startTime = System.currentTimeMillis();
        QueryResult result = new QueryResult();
        result.setQuestion(query);

        // 1. ROUTER: Rules and scope check
        QueryRouter.RoutingDecision routing = router.route(query);
        if (!routing.isAllowed()) {
            result.setStatus(routing.getStatus());
            result.setAnswer(routing.getRefusalMessage());
            result.setExecutionMode("ROUTER_DETERMINISTIC_REFUSAL");
            result.addAudit("Router check failed: " + routing.getStatus() + " -> routed to Refusal.");
            result.setLatencyMs(System.currentTimeMillis() - startTime);
            return result;
        }
        result.addAudit("Router check passed: Query is in-scope and free of prompt injection.");

        // 2. RETRIEVER: BM25 plus metadata filters
        BM25Retriever.RetrievalResult retrieval = retriever.retrieve(query, 5);
        result.setRetrievedFacts(retrieval.getFacts());
        result.addAudit("Retriever: Fetched " + retrieval.getFacts().size() + " facts (Confident: " + retrieval.isConfident() + ", TopScore: " + String.format("%.2f", retrieval.getTopScore()) + ").");

        if (retrieval.getFacts().isEmpty() || !retrieval.isConfident()) {
            // Out of knowledge base / unanswerable from facts
            QueryResult fallbackResult = fallback.generate(query, retrieval.getFacts(), false);
            fallbackResult.setAuditTrail(result.getAuditTrail());
            fallbackResult.addAudit("Retriever confidence below threshold -> deterministic refusal triggered.");
            fallbackResult.setLatencyMs(System.currentTimeMillis() - startTime);
            return fallbackResult;
        }

        // 3. GENERATOR & VERIFIER with retry loop
        if (enableLLM) {
            String violationFeedback = null;
            for (int retry = 0; retry < 2; retry++) {
                try {
                    result.addAudit("Generator: Invoking LLM (Attempt " + (retry + 1) + ")...");
                    LLMGenerator.LLMResult llmResult = generator.generateWithRetry(query, retrieval.getFacts(), violationFeedback);

                    result.addAudit("Verifier: Running deterministic claim & entity verification...");
                    VerificationResult verResult = verifier.verify(llmResult.answer, llmResult.claims, retrieval.getFacts(), retry);

                    if (verResult.isPassed()) {
                        result.setStatus(QueryResult.Status.SUCCESS);
                        result.setExecutionMode("OLLAMA_QWEN_VERIFIED");
                        result.setAnswer(llmResult.answer);
                        result.setCitedFactIds(verResult.getVerifiedFactIds());
                        result.setSourceReferences(formatSources(verResult.getVerifiedFactIds()));
                        result.addAudit("Verifier PASSED on attempt " + (retry + 1) + ". Citations verified: " + verResult.getVerifiedFactIds());
                        result.setLatencyMs(System.currentTimeMillis() - startTime);
                        return result;
                    } else {
                        violationFeedback = verResult.getViolationSummary();
                        result.addAudit("Verifier FAILED (Attempt " + (retry + 1) + "): " + violationFeedback);
                    }
                } catch (Exception ex) {
                    result.addAudit("Generator failed (API down, slow, or unusable: " + ex.getMessage() + ") -> Routing to Fallback.");
                    break; // Jump straight to fallback
                }
            }
        } else {
            result.addAudit("LLM disabled by configuration (DISABLE_LLM=true) -> Using deterministic fallback.");
        }

        // 4. FALLBACK: Extractive answer from retrieved facts
        QueryResult fallbackResult = fallback.generate(query, retrieval.getFacts(), true);
        fallbackResult.setAuditTrail(result.getAuditTrail());
        fallbackResult.addAudit("Fallback executed: Deterministic extractive synthesis from verified facts.");
        fallbackResult.setLatencyMs(System.currentTimeMillis() - startTime);
        return fallbackResult;
    }

    private List<String> formatSources(List<String> factIds) {
        List<String> sources = new ArrayList<>();
        for (String fid : factIds) {
            Fact f = factStore.getFact(fid);
            if (f != null) {
                sources.add("[" + f.getId() + "] " + f.getTitle() + " (Source: " + f.getSource() + ")");
            } else {
                sources.add("[" + fid + "] (Unregistered fact)");
            }
        }
        return sources;
    }

    public FactStore getFactStore() { return factStore; }
    public SQLiteIndex getIndex() { return index; }
}
