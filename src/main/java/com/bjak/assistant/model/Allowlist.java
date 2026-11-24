package com.bjak.assistant.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Strict allowlist of verified entities (employers, titles, technologies, active years).
 */
public class Allowlist {
    private List<String> employers = new ArrayList<>();
    private List<String> titles = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private List<String> years = new ArrayList<>();

    public Allowlist() {}

    public List<String> getEmployers() { return employers; }
    public void setEmployers(List<String> employers) { this.employers = employers; }

    public List<String> getTitles() { return titles; }
    public void setTitles(List<String> titles) { this.titles = titles; }

    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) { this.technologies = technologies; }

    public List<String> getYears() { return years; }
    public void setYears(List<String> years) { this.years = years; }

    public boolean isEmployerAllowed(String name) {
        if (name == null || name.isBlank()) return true;
        for (String emp : employers) {
            if (emp.equalsIgnoreCase(name.trim()) || name.toLowerCase().contains(emp.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public boolean isTitleAllowed(String title) {
        if (title == null || title.isBlank()) return true;
        for (String t : titles) {
            if (t.equalsIgnoreCase(title.trim()) || title.toLowerCase().contains(t.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
