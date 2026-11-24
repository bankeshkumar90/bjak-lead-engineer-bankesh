package com.bjak.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of deterministic claim and entity verification against allowlist and retrieved facts.
 */
public class VerificationResult {
    private boolean passed;
    private List<String> violations = new ArrayList<>();
    private List<String> verifiedFactIds = new ArrayList<>();
    private int retryCount = 0;

    public VerificationResult(boolean passed) {
        this.passed = passed;
    }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public List<String> getViolations() { return violations; }
    public void addViolation(String violation) { this.violations.add(violation); }

    public List<String> getVerifiedFactIds() { return verifiedFactIds; }
    public void setVerifiedFactIds(List<String> verifiedFactIds) { this.verifiedFactIds = verifiedFactIds; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getViolationSummary() {
        return String.join("; ", violations);
    }
}
