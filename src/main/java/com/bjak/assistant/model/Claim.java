package com.bjak.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Individual statement or claim asserted by the model, mapped to supporting fact IDs.
 */
public class Claim {
    private String text;
    private List<String> factIds = new ArrayList<>();

    public Claim() {}

    public Claim(String text, List<String> factIds) {
        this.text = text;
        if (factIds != null) this.factIds = factIds;
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<String> getFactIds() { return factIds; }
    public void setFactIds(List<String> factIds) { this.factIds = factIds; }

    @Override
    public String toString() {
        return text + " " + factIds;
    }
}
