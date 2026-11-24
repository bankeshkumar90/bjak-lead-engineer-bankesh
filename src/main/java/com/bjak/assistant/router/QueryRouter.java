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
            Pattern.compile("(?i)capital\\s+(of|city)\\s+[a-z]+"),
            Pattern.compile("(?i)(how\\s+to\\s+|recipe\\s+(for|to)|how\\s+do\\s+i\\s+)?(bake|cook|make)\\s+[a-z]+"),
            Pattern.compile("(?i)weather\\s+(in|at|today)"),
            Pattern.compile("(?i)(write|compose)\\s+.*?(poem|song|story|joke|haiku|lyrics)"),
            Pattern.compile("(?i)stock\\s+price\\s+of"),
            Pattern.compile("(?i)who\\s+won\\s+the\\s+(world\\s+cup|super\\s+bowl|election|match)"),
            Pattern.compile("(?i)calculate\\s+\\d+|what\\s+is\\s+\\d+\\s*[\\+\\-\\*/]\\s*\\d+"),
            Pattern.compile("(?i)translate\\s+.*\\s+to\\s+[a-z]+"),
            Pattern.compile("(?i)(write|implement|create)\\s+(a\\s+)?(python|java|javascript|c\\+\\+|rust|go|c)\\s+(script|program|code|function|algorithm)"),
            Pattern.compile("(?i)(quicksort|bubble\\s+sort|binary\\s+search|linked\\s+list|fibonacci)"),
            Pattern.compile("(?i)who\\s+is\\s+(the\\s+)?(president|prime\\s+minister|king|queen|elon\\s+musk|bill\\s+gates|jeff\\s+bezos|sam\\s+altman)"),
            Pattern.compile("(?i)(fix|repair)\\s+(a\\s+)?(leaking|leak|water\\s+pipe|pipe|sink|faucet|car|toilet)"),
            Pattern.compile("(?i)(symptoms|treatment|remedies|remedy|cure)\\s+(for|of)\\s+(the\\s+)?(cold|flu|cough|headache|fever|covid)"),
            Pattern.compile("(?i)(highest\\s+grossing|best|top\\s+\\d+)\\s+(movies|films|songs|albums)")
    );

    public static final String OUT_OF_SCOPE_REFUSAL_MESSAGE =
            "Refusal [Out of Scope]: This inquiry is outside the scope of Bankesh Kumar's professional career documentation. " +
            "I am an AI Career Assistant dedicated exclusively to Bankesh Kumar's professional experience, architecture projects, engineering leadership, and verified qualifications. " +
            "I do not answer general knowledge, weather, cooking recipes, creative writing, health remedies, generic coding exercises, or external domain queries.\n\n" +
            "Please feel free to ask about Bankesh Kumar's verified profile and technical expertise, such as:\n" +
            "• Enterprise Mobile Platforms: React Native, Android (Kotlin), iOS (Swift), Jetpack Compose, Kotlin Multiplatform (KMM), and Clean Architecture / MVVM\n" +
            "• Flagship Systems: CabCharge white-label automotive taxi platform, Aviva insurance/wellbeing app, ZoomCar (Zoom One unified app), and Tekion OTT TV & RFID displays\n" +
            "• Backend Microservices: Spring Boot, Spring Cloud, Eureka, API Gateway, and Docker containerization\n" +
            "• Privacy-First Offline AI: Privacy-first offline AI workflows utilizing Ollama, local Qwen (e.g. Qwen 3 8B), N8n, and Docker\n" +
            "• Engineering Leadership: Leading cross-functional teams of up to 22 engineers, delivering 30+ production apps across 8+ industry domains, and CMMI Level 5 standards\n\n" +
            "I do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. " +
            "As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, speculate, or fabricate qualifications, employers, or metrics.";

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

        // 1b. Check pure greetings ("Hi", "Hello", etc.)
        if (isGreeting(trimmed)) {
            return new RoutingDecision(false, QueryResult.Status.REFUSED_UNANSWERABLE,
                    "Hello! Please ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering leadership.\n\n" +
                    "I do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. " +
                    "As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, " +
                    "speculate, or fabricate qualifications, employers, or metrics.");
        }

        // 2. Check unanswerable private/undocumented queries
        for (Pattern p : UNANSWERABLE_PRIVATE_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return new RoutingDecision(false, QueryResult.Status.REFUSED_UNANSWERABLE,
                        "Please feel free to ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering achievements.\n\n" +
                        "I do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. " +
                        "As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, " +
                        "speculate, or fabricate qualifications, employers, salary, or personal metrics.");
            }
        }

        // 3. Check out-of-scope patterns
        for (Pattern p : OUT_OF_SCOPE_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return new RoutingDecision(false, QueryResult.Status.REFUSED_OUT_OF_SCOPE,
                        OUT_OF_SCOPE_REFUSAL_MESSAGE);
            }
        }

        // 4. Allowed into retrieval pipeline
        return new RoutingDecision(true, QueryResult.Status.SUCCESS, null);
    }

    private static boolean isGreeting(String query) {
        if (query == null) return false;
        String lower = query.toLowerCase().trim();
        lower = lower.replaceAll("[!.,?]+$", "").trim();
        return lower.matches("(?i)^(hi|hello|hey|greetings|howdy|hola|yo|hiya|good\\s+(morning|afternoon|evening|day))(\\s+(there|bankesh|assistant|ai|kumar))?$");
    }
}
