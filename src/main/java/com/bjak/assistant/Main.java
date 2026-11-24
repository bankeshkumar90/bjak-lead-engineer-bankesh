package com.bjak.assistant;

import com.bjak.assistant.eval.EvalRunner;
import com.bjak.assistant.model.Fact;
import com.bjak.assistant.model.QueryResult;
import com.bjak.assistant.pipeline.CareerAssistantPipeline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Career Assistant CLI Application (matching Picocli / CLI specification).
 * Provides interactive Q&A, single-query execution, evaluation runner, and source inspection.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length == 0 || "-h".equals(args[0]) || "--help".equals(args[0]) || "help".equals(args[0])) {
            printHelp();
            return;
        }

        String command = args[0].toLowerCase();
        try {
            CareerAssistantPipeline pipeline = CareerAssistantPipeline.createDefault();

            switch (command) {
                case "ask":
                    if (args.length < 2) {
                        System.err.println("Error: Missing question argument. Usage: ./run.sh ask \"<your question>\"");
                        System.exit(1);
                    }
                    String question = args[1];
                    QueryResult result = pipeline.process(question);
                    System.out.println(result.formatCLIOutput());
                    break;

                case "chat":
                case "interactive":
                    runInteractive(pipeline);
                    break;

                case "web":
                case "server":
                case "ui":
                    int port = 8080;
                    if (args.length > 1) {
                        try {
                            port = Integer.parseInt(args[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                    com.bjak.assistant.web.WebServer webServer = new com.bjak.assistant.web.WebServer(pipeline, port);
                    webServer.start();
                    // Keep main thread alive
                    Thread.currentThread().join();
                    break;

                case "eval":
                    EvalRunner evalRunner = new EvalRunner(pipeline);
                    evalRunner.runEvaluation();
                    break;

                case "index":
                    System.out.println("Re-indexing facts into SQLite FTS5 database...");
                    pipeline.getIndex().indexFacts(pipeline.getFactStore().getAllFacts());
                    System.out.println("Indexed " + pipeline.getFactStore().size() + " facts successfully into facts.db (SQLite FTS5).");
                    break;

                case "sources":
                    printSources(pipeline);
                    break;

                default:
                    // If no explicit sub-command keyword was matched, treat the argument string as a direct question!
                    // This makes it maximally plug-and-play: ./run.sh "What did Bankesh build?"
                    StringBuilder directQuestion = new StringBuilder();
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) directQuestion.append(" ");
                        directQuestion.append(args[i]);
                    }
                    QueryResult directResult = pipeline.process(directQuestion.toString());
                    System.out.println(directResult.formatCLIOutput());
                    break;
            }
        } catch (Exception e) {
            System.err.println("Fatal Error initializing Career Assistant: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runInteractive(CareerAssistantPipeline pipeline) {
        System.out.println("╔═════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║         Bankesh Kumar — Career Assistant (Interactive Mode)             ║");
        System.out.println("║  Grounded technical interview AI with strict verification & provenance  ║");
        System.out.println("╚═════════════════════════════════════════════════════════════════════════╝");
        System.out.println("Type your question below. Type 'exit', 'quit', or 'q' to leave.\n");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("\n[Interviewer] > ");
                String input = reader.readLine();
                if (input == null) break;
                input = input.trim();
                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
                    System.out.println("Exiting. Thank you for your time!");
                    break;
                }
                if (input.isEmpty()) continue;

                QueryResult res = pipeline.process(input);
                System.out.println("\n" + res.formatCLIOutput());
            }
        } catch (Exception e) {
            System.err.println("Error reading input: " + e.getMessage());
        }
    }

    private static void printSources(CareerAssistantPipeline pipeline) {
        List<Fact> facts = pipeline.getFactStore().getAllFacts();
        System.out.println("Registered Verified Facts (" + facts.size() + " total):");
        System.out.println("──────────────────────────────────────────────────────────────────────────");
        for (Fact f : facts) {
            System.out.printf("[%s] [%-14s] %-35s (Source: %s)%n",
                    f.getId(), f.getCategory(), f.getTitle(), f.getSource());
        }
        System.out.println("──────────────────────────────────────────────────────────────────────────");
    }

    private static void printHelp() {
        System.out.println("Bankesh Kumar — Career Assistant CLI");
        System.out.println("Usage: ./run.sh <command> [arguments]\n");
        System.out.println("Commands:");
        System.out.println("  web [port]             Start web browser UI (default port: 8080)");
        System.out.println("  ask \"<question>\"       Ask a single question on Bankesh's experience");
        System.out.println("  \"<question>\"           Direct question shortcut (plug-and-play)");
        System.out.println("  interactive / chat     Start interactive Q&A session");
        System.out.println("  eval                   Execute the 25-question evaluation suite & print metrics table");
        System.out.println("  sources                List all registered facts and provenance documents");
        System.out.println("  index                  Rebuild SQLite FTS5 index from facts.yaml");
        System.out.println("  help                   Show this help message\n");
        System.out.println("Examples:");
        System.out.println("  ./run.sh ask \"What is Bankesh's experience with Kafka and payment ledgers?\"");
        System.out.println("  ./run.sh ask \"What is the discrepancy between your CV and LinkedIn?\"");
        System.out.println("  ./run.sh eval");
    }
}
