package com.bjak.assistant.eval;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Data structure and loader for evaluation test cases.
 */
public class EvalDataset {

    public static class TestCase {
        public String id;
        public String category;
        public String question;
        public String expectedStatus;
        public List<String> requiredContains = new ArrayList<>();
        public List<String> forbiddenContains = new ArrayList<>();
        public List<String> expectedFactIds = new ArrayList<>();
    }

    public static List<TestCase> loadDefault() throws IOException {
        File file = findFile("eval/dataset.json");
        if (file == null || !file.exists()) {
            throw new IOException("Could not find eval/dataset.json");
        }
        return parseJson(file.toPath());
    }

    private static File findFile(String relativePath) {
        File f1 = new File(relativePath);
        if (f1.exists()) return f1;
        File f2 = new File("../" + relativePath);
        if (f2.exists()) return f2;
        String userDir = System.getProperty("user.dir", ".");
        File f3 = new File(userDir, relativePath);
        if (f3.exists()) return f3;
        return null;
    }

    public static List<TestCase> parseJson(Path path) throws IOException {
        String json = Files.readString(path).trim();
        List<TestCase> cases = new ArrayList<>();

        int idx = 0;
        while ((idx = json.indexOf('{', idx)) != -1) {
            int closeIdx = findMatchingBrace(json, idx);
            if (closeIdx == -1) break;
            String objStr = json.substring(idx, closeIdx + 1);
            TestCase tc = new TestCase();
            tc.id = extractString(objStr, "id");
            tc.category = extractString(objStr, "category");
            tc.question = extractString(objStr, "question");
            tc.expectedStatus = extractString(objStr, "expected_status");
            tc.requiredContains = extractList(objStr, "required_contains");
            tc.forbiddenContains = extractList(objStr, "forbidden_contains");
            tc.expectedFactIds = extractList(objStr, "expected_fact_ids");

            if (tc.id != null && !tc.id.isEmpty()) {
                cases.add(tc);
            }
            idx = closeIdx + 1;
        }
        return cases;
    }

    private static int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        boolean inQuote = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static String extractString(String objStr, String key) {
        int keyPos = objStr.indexOf("\"" + key + "\"");
        if (keyPos == -1) return "";
        int colon = objStr.indexOf(':', keyPos);
        int quoteStart = objStr.indexOf('"', colon);
        if (quoteStart == -1) return "";
        int quoteEnd = objStr.indexOf('"', quoteStart + 1);
        while (quoteEnd != -1 && objStr.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = objStr.indexOf('"', quoteEnd + 1);
        }
        if (quoteEnd == -1) return "";
        return objStr.substring(quoteStart + 1, quoteEnd);
    }

    private static List<String> extractList(String objStr, String key) {
        List<String> list = new ArrayList<>();
        int keyPos = objStr.indexOf("\"" + key + "\"");
        if (keyPos == -1) return list;
        int bracketStart = objStr.indexOf('[', keyPos);
        int bracketEnd = objStr.indexOf(']', bracketStart);
        if (bracketStart != -1 && bracketEnd != -1) {
            String inner = objStr.substring(bracketStart + 1, bracketEnd);
            String[] tokens = inner.split(",");
            for (String t : tokens) {
                String clean = t.trim().replaceAll("^\"|\"$", "").trim();
                if (!clean.isEmpty()) list.add(clean);
            }
        }
        return list;
    }
}
