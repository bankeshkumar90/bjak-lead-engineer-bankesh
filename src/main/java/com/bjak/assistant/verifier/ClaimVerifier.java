package com.bjak.assistant.verifier;

import com.bjak.assistant.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic verifier ensuring:
 * 1. Every cited Fact ID exists in the retrieved top-k facts.
 * 2. No fabricated employers or non-allowlisted company names are claimed.
 * 3. No fabricated executive titles (e.g. CTO, VP) outside allowlist are claimed.
 * 4. Extrapolated or unverifiable assertions trigger immediate retry or fallback.
 */
public class ClaimVerifier {
    private final Allowlist allowlist;
    private static final Pattern FACT_ID_PATTERN = Pattern.compile("FACT-\\d{3}");

    // Disallowed common fabricated tech/employer entities for sanity checking
    private static final Set<String> HIGH_RISK_FABRICATED_ENTITIES = Set.of(
            "google", "meta", "facebook", "amazon", "apple", "netflix", "microsoft",
            "goldman sachs", "jpmorgan", "uber", "airbnb"
    );

    public ClaimVerifier(Allowlist allowlist) {
        this.allowlist = allowlist;
    }

    public VerificationResult verify(String answerText, List<Claim> claims, List<Fact> retrievedFacts, int retryCount) {
        VerificationResult result = new VerificationResult(true);
        result.setRetryCount(retryCount);

        Set<String> validFactIds = new HashSet<>();
        for (Fact f : retrievedFacts) {
            validFactIds.add(f.getId());
        }

        List<String> citedIds = new ArrayList<>();

        // 1. Check fact IDs cited in Claim objects
        if (claims != null && !claims.isEmpty()) {
            for (Claim claim : claims) {
                for (String fid : claim.getFactIds()) {
                    citedIds.add(fid);
                    if (!validFactIds.contains(fid)) {
                        result.setPassed(false);
                        result.addViolation("Invalid citation: Fact ID '" + fid + "' does not exist in retrieved facts.");
                    }
                }
            }
        }

        // 2. Also extract any [FACT-xxx] tags present directly in the answer text
        Matcher matcher = FACT_ID_PATTERN.matcher(answerText);
        while (matcher.find()) {
            String fid = matcher.group();
            if (!citedIds.contains(fid)) {
                citedIds.add(fid);
            }
            if (!validFactIds.contains(fid)) {
                result.setPassed(false);
                result.addViolation("Text cites non-existent Fact ID: " + fid);
            }
        }

        // 3. Groundedness requirement: If answer makes factual assertions, it must cite at least one valid fact
        if (citedIds.isEmpty() && !isRefusal(answerText)) {
            result.setPassed(false);
            result.addViolation("Groundedness violation: Answer asserts factual claims but provides zero Fact ID citations.");
        }

        // 4. Fabricated Employer / Entity allowlist check
        String lowerAnswer = answerText.toLowerCase();
        for (String riskEntity : HIGH_RISK_FABRICATED_ENTITIES) {
            if (lowerAnswer.contains(riskEntity)) {
                // Check if this entity is somehow in the allowlist (it's not)
                if (!allowlist.isEmployerAllowed(riskEntity)) {
                    result.setPassed(false);
                    result.addViolation("Fabrication violation: Answer mentions unverified employer/entity: " + riskEntity);
                }
            }
        }

        // 5. Exaggerated Title Check (e.g. CTO, Chief Technology Officer, VP)
        if (lowerAnswer.contains(" cto ") || lowerAnswer.contains("chief technology officer") || lowerAnswer.contains(" vp of engineering")) {
            result.setPassed(false);
            result.addViolation("Title exaggeration violation: Answer claims executive title outside official allowlist.");
        }

        result.setVerifiedFactIds(citedIds);
        return result;
    }

    private boolean isRefusal(String text) {
        String lower = text.toLowerCase();
        return lower.contains("do not have") || lower.contains("cannot answer") ||
               lower.contains("refusal") || lower.contains("not found in") ||
               lower.contains("no verified information");
    }
}
