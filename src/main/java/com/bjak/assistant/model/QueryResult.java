package com.bjak.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete response emitted by the Career Assistant pipeline.
 */
public class QueryResult {
    public enum Status {
        SUCCESS,
        REFUSED_INJECTION,
        REFUSED_OUT_OF_SCOPE,
        REFUSED_UNANSWERABLE,
        FALLBACK_EXTRACTIVE,
        ERROR
    }

    private String question;
    private String answer;
    private Status status;
    private String executionMode; // e.g. "OLLAMA_QWEN_VERIFIED", "FALLBACK_DETERMINISTIC", "ROUTER_REFUSED"
    private List<Fact> retrievedFacts = new ArrayList<>();
    private List<String> citedFactIds = new ArrayList<>();
    private List<String> sourceReferences = new ArrayList<>();
    private List<String> auditTrail = new ArrayList<>();
    private long latencyMs;

    public QueryResult() {}

    public QueryResult(String question, String answer, Status status, String executionMode) {
        this.question = question;
        this.answer = answer;
        this.status = status;
        this.executionMode = executionMode;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public List<Fact> getRetrievedFacts() { return retrievedFacts; }
    public void setRetrievedFacts(List<Fact> retrievedFacts) { this.retrievedFacts = retrievedFacts; }

    public List<String> getCitedFactIds() { return citedFactIds; }
    public void setCitedFactIds(List<String> citedFactIds) { this.citedFactIds = citedFactIds; }

    public List<String> getSourceReferences() { return sourceReferences; }
    public void setSourceReferences(List<String> sourceReferences) { this.sourceReferences = sourceReferences; }

    public List<String> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<String> auditTrail) { 
        this.auditTrail = new ArrayList<>(auditTrail); 
    }
    public void addAudit(String log) { this.auditTrail.add(log); }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public String formatCLIOutput() {
        StringBuilder sb = new StringBuilder();
        sb.append("══════════════════════════════════════════════════════════════════════════\n");
        sb.append("QUESTION: ").append(question).append("\n");
        sb.append("STATUS:   ").append(status).append(" (Mode: ").append(executionMode)
          .append(", Latency: ").append(latencyMs).append("ms)\n");
        sb.append("──────────────────────────────────────────────────────────────────────────\n");
        sb.append("ANSWER:\n").append(answer).append("\n");
        sb.append("──────────────────────────────────────────────────────────────────────────\n");
        sb.append("SOURCES & CITATIONS:\n");
        if (sourceReferences.isEmpty()) {
            sb.append("  (None / Refusal)\n");
        } else {
            for (String src : sourceReferences) {
                sb.append("  • ").append(src).append("\n");
            }
        }
        sb.append("──────────────────────────────────────────────────────────────────────────\n");
        sb.append("AUDIT TRAIL:\n");
        for (String audit : auditTrail) {
            sb.append("  [PIPELINE] ").append(audit).append("\n");
        }
        sb.append("══════════════════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
