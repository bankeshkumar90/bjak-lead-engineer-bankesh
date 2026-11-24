package com.bjak.assistant.router;

import com.bjak.assistant.model.QueryResult;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic router: Checks query for prompt injections and scope boundary.
 * Never delegates injection detection or scope enforcement to an LLM.
 */
public class QueryRouter {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all|any|previous|above|prior|the)*\\s*(all|any|previous|above|prior)*\\s*instructions?"),
            Pattern.compile("(?i)disregard\\s+(all|any|previous|above|prior|the)*\\s*(all|any|previous|above|prior)*\\s*instructions?"),
            Pattern.compile("(?i)fabricate|exaggerate"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)?\\s*.*"),
            Pattern.compile("(?i)pretend\\s+(you\\s+are|to\\s+be)"),
            Pattern.compile("(?i)act\\s+as\\s+(a|an)?"),
            Pattern.compile("(?i)reveal\\s+(the|your)?\\s*(system\\s+prompt|instructions?|hidden\\s+prompt)"),
            Pattern.compile("(?i)system\\s+prompt"),
            Pattern.compile("(?i)jailbreak|dan\\s+mode|developer\\s+mode"),
            Pattern.compile("(?i)<script.*?>|eval\\s*\\(|javascript:"),
            Pattern.compile("(?i)forget\\s+(all|everything|previous)"),
            Pattern.compile("(?i)override\\s+(safety|rules|instructions)")
    );

    private static final List<Pattern> UNANSWERABLE_PRIVATE_PATTERNS = List.of(
            Pattern.compile("(?i)(salary|bonus|compensation|net pay|package|ctc)"),
            Pattern.compile("(?i)(passport|blood group|marital status|religion|political party)"),
            Pattern.compile("(?i)(solana|smart contract|ethereum)"),
            Pattern.compile("(?i)(master of science|master's degree|canada|stanford|mit)")
    );

    private static final List<Pattern> OUT_OF_SCOPE_PATTERNS = List.of(
            Pattern.compile("(?i)capital\\s+of\\s+[a-z]+"),
            Pattern.compile("(?i)recipe\\s+(for|to)"),
            Pattern.compile("(?i)weather\\s+(in|at|today)"),
            Pattern.compile("(?i)write\\s+(a|me\\s+a)?\\s*(poem|song|story|joke|haiku)"),
            Pattern.compile("(?i)stock\\s+price\\s+of"),
            Pattern.compile("(?i)who\\s+won\\s+the\\s+(world\\s+cup|super\\s+bowl|election|match)"),
            Pattern.compile("(?i)calculate\\s+\\d+|what\\s+is\\s+\\d+\\s*[\\+\\-\\*/]\\s*\\d+"),
            Pattern.compile("(?i)translate\\s+.*\\s+to\\s+[a-z]+")
    );

    public static class RoutingDecision {
        private final boolean allowed;
        private final QueryResult.Status status;
        private final String refusalMessage;

        public RoutingDecision(boolean allowed, QueryResult.Status status, String refusalMessage) {
            this.allowed = allowed;
            this.status = status;
            this.refusalMessage = refusalMessage;
        }

        public boolean isAllowed() { return allowed; }
        public QueryResult.Status getStatus() { return status; }
        public String getRefusalMessage() { return refusalMessage; }
    }

    public RoutingDecision route(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new RoutingDecision(false, QueryResult.Status.REFUSED_OUT_OF_SCOPE,
                    "Refusal: Empty question provided. Please submit a question regarding Bankesh Kumar's professional background.");
        }

        String trimmed = query.trim();

        // 1. Check prompt injection
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return new RoutingDecision(false, QueryResult.Status.REFUSED_INJECTION,
                        "Refusal [Safety/Security]: Query contains instruction override or prompt injection attempt. " +
                        "The assistant strictly answers questions grounded in Bankesh Kumar's verified professional record.");
            }
        }

        // 2. Check unanswerable private/undocumented queries
        for (Pattern p : UNANSWERABLE_PRIVATE_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return new RoutingDecision(false, QueryResult.Status.REFUSED_UNANSWERABLE,
                        "I do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. " +
                        "As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, " +
                        "speculate, or fabricate qualifications, employers, salary, or personal metrics.");
            }
        }

        // 3. Check out-of-scope patterns
        for (Pattern p : OUT_OF_SCOPE_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return new RoutingDecision(false, QueryResult.Status.REFUSED_OUT_OF_SCOPE,
                        "Refusal [Out of Scope]: I am an AI Career Assistant dedicated exclusively to Bankesh Kumar's " +
                        "professional experience, architecture projects, engineering leadership, and qualifications. " +
                        "I do not answer general knowledge, weather, creative writing, or external domain queries.");
            }
        }

        // 4. Allowed into retrieval pipeline
        return new RoutingDecision(true, QueryResult.Status.SUCCESS, null);
    }
}
