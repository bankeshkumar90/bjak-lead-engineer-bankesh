package com.bjak.assistant.web;

import com.bjak.assistant.model.Fact;
import com.bjak.assistant.model.QueryResult;
import com.bjak.assistant.pipeline.CareerAssistantPipeline;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Built-in zero-dependency Web Server providing a rich browser-based interface
 * for asking questions, viewing verified citations, and inspecting the pipeline audit trail.
 */
public class WebServer {
    private final CareerAssistantPipeline pipeline;
    private final int port;
    private HttpServer server;

    public WebServer(CareerAssistantPipeline pipeline, int port) {
        this.pipeline = pipeline;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/api/ask", new AskHandler(pipeline));
        server.createContext("/api/sources", new SourcesHandler(pipeline));
        server.setExecutor(null);
        server.start();
        System.out.println("╔═════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║            Career Assistant Web Interface Started Successfully!         ║");
        System.out.println("║                                                                         ║");
        System.out.printf("║  👉 Open in your browser:  http://localhost:%-26d  ║%n", port);
        System.out.println("║  Press Ctrl+C to stop the server                                        ║");
        System.out.println("╚═════════════════════════════════════════════════════════════════════════╝");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] htmlBytes = getHtmlContent().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }
    }

    static class AskHandler implements HttpHandler {
        private final CareerAssistantPipeline pipeline;

        public AskHandler(CareerAssistantPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                setCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            setCors(exchange);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String question = extractQuestion(body);

            if (question.isBlank()) {
                sendJson(exchange, 400, "{\"error\":\"Missing question in request body\"}");
                return;
            }

            QueryResult result = pipeline.process(question);
            String json = toJson(result);
            sendJson(exchange, 200, json);
        }

        private String extractQuestion(String body) {
            int qIdx = body.indexOf("\"question\":");
            if (qIdx == -1) return "";
            int start = body.indexOf('"', qIdx + 11);
            if (start == -1) return "";
            int end = body.indexOf('"', start + 1);
            while (end != -1 && body.charAt(end - 1) == '\\') {
                end = body.indexOf('"', end + 1);
            }
            if (end == -1) return "";
            return body.substring(start + 1, end).replace("\\\"", "\"").replace("\\\\", "\\").trim();
        }

        private String toJson(QueryResult qr) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"question\":\"").append(escapeJson(qr.getQuestion())).append("\",");
            sb.append("\"answer\":\"").append(escapeJson(qr.getAnswer())).append("\",");
            sb.append("\"status\":\"").append(qr.getStatus()).append("\",");
            sb.append("\"executionMode\":\"").append(escapeJson(qr.getExecutionMode())).append("\",");
            sb.append("\"latencyMs\":").append(qr.getLatencyMs()).append(",");

            sb.append("\"citedFactIds\":[");
            for (int i = 0; i < qr.getCitedFactIds().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(qr.getCitedFactIds().get(i))).append("\"");
            }
            sb.append("],");

            sb.append("\"sourceReferences\":[");
            for (int i = 0; i < qr.getSourceReferences().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(qr.getSourceReferences().get(i))).append("\"");
            }
            sb.append("],");

            sb.append("\"auditTrail\":[");
            for (int i = 0; i < qr.getAuditTrail().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(qr.getAuditTrail().get(i))).append("\"");
            }
            sb.append("]");

            sb.append("}");
            return sb.toString();
        }

        private void setCors(HttpExchange exchange) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }

        private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class SourcesHandler implements HttpHandler {
        private final CareerAssistantPipeline pipeline;

        public SourcesHandler(CareerAssistantPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<Fact> facts = pipeline.getFactStore().getAllFacts();
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (int i = 0; i < facts.size(); i++) {
                Fact f = facts.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                  .append("\"id\":\"").append(escapeJson(f.getId())).append("\",")
                  .append("\"category\":\"").append(escapeJson(f.getCategory())).append("\",")
                  .append("\"title\":\"").append(escapeJson(f.getTitle())).append("\",")
                  .append("\"source\":\"").append(escapeJson(f.getSource())).append("\",")
                  .append("\"content\":\"").append(escapeJson(f.getContent())).append("\"")
                  .append("}");
            }
            sb.append("]");
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String getHtmlContent() {
        return "<!DOCTYPE html>\n" +
"<html lang=\"en\">\n" +
"<head>\n" +
"  <meta charset=\"UTF-8\" />\n" +
"  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n" +
"  <title>Bankesh Kumar — Career Assistant</title>\n" +
"  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n" +
"  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n" +
"  <link href=\"https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap\" rel=\"stylesheet\">\n" +
"  <style>\n" +
"    :root {\n" +
"      --bg: #0B0F19;\n" +
"      --surface: #121826;\n" +
"      --surface-border: #1E293B;\n" +
"      --surface-hover: #1A2234;\n" +
"      --primary: #38BDF8;\n" +
"      --primary-glow: rgba(56, 189, 248, 0.15);\n" +
"      --accent: #818CF8;\n" +
"      --text: #F1F5F9;\n" +
"      --text-muted: #94A3B8;\n" +
"      --badge-success: #10B981;\n" +
"      --badge-refusal: #F59E0B;\n" +
"      --font-sans: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;\n" +
"      --font-mono: 'JetBrains Mono', monospace;\n" +
"    }\n" +
"    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
"    body {\n" +
"      background: var(--bg);\n" +
"      color: var(--text);\n" +
"      font-family: var(--font-sans);\n" +
"      min-height: 100vh;\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"    }\n" +
"    header {\n" +
"      background: var(--surface);\n" +
"      border-bottom: 1px solid var(--surface-border);\n" +
"      padding: 1rem 1.5rem;\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      justify-content: space-between;\n" +
"      position: sticky;\n" +
"      top: 0;\n" +
"      z-index: 100;\n" +
"      backdrop-filter: blur(12px);\n" +
"    }\n" +
"    .brand {\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      gap: 0.75rem;\n" +
"    }\n" +
"    .avatar {\n" +
"      width: 40px;\n" +
"      height: 40px;\n" +
"      border-radius: 10px;\n" +
"      background: linear-gradient(135deg, var(--primary), var(--accent));\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      justify-content: center;\n" +
"      font-weight: 700;\n" +
"      color: #0F172A;\n" +
"    }\n" +
"    .brand-title { font-size: 1.05rem; font-weight: 700; }\n" +
"    .brand-subtitle { font-size: 0.75rem; color: var(--text-muted); }\n" +
"    .nav-actions {\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      gap: 0.75rem;\n" +
"    }\n" +
"    .btn-outline {\n" +
"      background: transparent;\n" +
"      border: 1px solid var(--surface-border);\n" +
"      color: var(--text-muted);\n" +
"      padding: 0.45rem 0.85rem;\n" +
"      border-radius: 8px;\n" +
"      font-size: 0.8rem;\n" +
"      cursor: pointer;\n" +
"      transition: all 0.2s;\n" +
"      display: inline-flex;\n" +
"      align-items: center;\n" +
"      gap: 0.4rem;\n" +
"      text-decoration: none;\n" +
"    }\n" +
"    .btn-outline:hover {\n" +
"      border-color: var(--primary);\n" +
"      color: var(--primary);\n" +
"    }\n" +
"    main {\n" +
"      flex: 1;\n" +
"      max-width: 900px;\n" +
"      width: 100%;\n" +
"      margin: 0 auto;\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      padding: 1.5rem 1rem;\n" +
"      gap: 1.25rem;\n" +
"    }\n" +
"    .chips-label { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); margin-bottom: 0.5rem; }\n" +
"    .chips-row {\n" +
"      display: flex;\n" +
"      flex-wrap: wrap;\n" +
"      gap: 0.5rem;\n" +
"    }\n" +
"    .chip {\n" +
"      background: var(--surface);\n" +
"      border: 1px solid var(--surface-border);\n" +
"      color: var(--text);\n" +
"      padding: 0.4rem 0.8rem;\n" +
"      border-radius: 20px;\n" +
"      font-size: 0.8rem;\n" +
"      cursor: pointer;\n" +
"      transition: all 0.2s;\n" +
"    }\n" +
"    .chip:hover {\n" +
"      border-color: var(--primary);\n" +
"      background: var(--surface-hover);\n" +
"      color: var(--primary);\n" +
"    }\n" +
"    #chatFeed {\n" +
"      flex: 1;\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      gap: 1.25rem;\n" +
"      min-height: 380px;\n" +
"    }\n" +
"    .msg {\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      gap: 0.5rem;\n" +
"      animation: fadeIn 0.25s ease-out;\n" +
"    }\n" +
"    @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }\n" +
"    .msg-user {\n" +
"      align-self: flex-end;\n" +
"      max-width: 80%;\n" +
"    }\n" +
"    .msg-user .bubble {\n" +
"      background: linear-gradient(135deg, #0284C7, #2563EB);\n" +
"      color: #fff;\n" +
"      padding: 0.85rem 1.15rem;\n" +
"      border-radius: 16px 16px 4px 16px;\n" +
"      font-size: 0.92rem;\n" +
"      line-height: 1.45;\n" +
"    }\n" +
"    .msg-assistant {\n" +
"      align-self: flex-start;\n" +
"      width: 100%;\n" +
"    }\n" +
"    .msg-assistant .card {\n" +
"      background: var(--surface);\n" +
"      border: 1px solid var(--surface-border);\n" +
"      border-radius: 14px;\n" +
"      padding: 1.15rem 1.25rem;\n" +
"      display: flex;\n" +
"      flex-direction: column;\n" +
"      gap: 0.85rem;\n" +
"    }\n" +
"    .card-meta {\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      justify-content: space-between;\n" +
"      font-size: 0.75rem;\n" +
"      color: var(--text-muted);\n" +
"      padding-bottom: 0.5rem;\n" +
"      border-bottom: 1px solid rgba(255, 255, 255, 0.05);\n" +
"    }\n" +
"    .badge {\n" +
"      display: inline-flex;\n" +
"      align-items: center;\n" +
"      gap: 0.3rem;\n" +
"      padding: 0.2rem 0.5rem;\n" +
"      border-radius: 4px;\n" +
"      font-family: var(--font-mono);\n" +
"      font-size: 0.72rem;\n" +
"      font-weight: 500;\n" +
"    }\n" +
"    .badge-success { background: rgba(16, 185, 129, 0.15); color: #34D399; }\n" +
"    .badge-refusal { background: rgba(245, 158, 11, 0.15); color: #FBBF24; }\n" +
"    .card-body {\n" +
"      font-size: 0.92rem;\n" +
"      line-height: 1.6;\n" +
"      white-space: pre-wrap;\n" +
"    }\n" +
"    .card-sources {\n" +
"      background: rgba(15, 23, 42, 0.6);\n" +
"      border: 1px solid var(--surface-border);\n" +
"      border-radius: 8px;\n" +
"      padding: 0.75rem 1rem;\n" +
"      font-size: 0.8rem;\n" +
"    }\n" +
"    .sources-title {\n" +
"      font-weight: 600;\n" +
"      color: var(--primary);\n" +
"      margin-bottom: 0.4rem;\n" +
"      font-size: 0.75rem;\n" +
"      text-transform: uppercase;\n" +
"      letter-spacing: 0.05em;\n" +
"    }\n" +
"    .source-item {\n" +
"      color: var(--text-muted);\n" +
"      margin-bottom: 0.25rem;\n" +
"      display: flex;\n" +
"      gap: 0.5rem;\n" +
"    }\n" +
"    .source-tag {\n" +
"      color: var(--primary);\n" +
"      font-family: var(--font-mono);\n" +
"      font-weight: 500;\n" +
"      flex-shrink: 0;\n" +
"    }\n" +
"    .audit-toggle {\n" +
"      font-size: 0.75rem;\n" +
"      color: var(--text-muted);\n" +
"      cursor: pointer;\n" +
"      text-decoration: underline;\n" +
"      margin-top: 0.25rem;\n" +
"      display: inline-block;\n" +
"    }\n" +
"    .audit-box {\n" +
"      display: none;\n" +
"      font-family: var(--font-mono);\n" +
"      font-size: 0.72rem;\n" +
"      color: #94A3B8;\n" +
"      background: #020617;\n" +
"      padding: 0.6rem 0.8rem;\n" +
"      border-radius: 6px;\n" +
"      margin-top: 0.5rem;\n" +
"      line-height: 1.5;\n" +
"    }\n" +
"    .input-bar {\n" +
"      position: sticky;\n" +
"      bottom: 1.5rem;\n" +
"      background: var(--surface);\n" +
"      border: 1px solid var(--surface-border);\n" +
"      border-radius: 16px;\n" +
"      padding: 0.5rem 0.6rem 0.5rem 1.25rem;\n" +
"      display: flex;\n" +
"      align-items: center;\n" +
"      gap: 0.75rem;\n" +
"      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4), 0 0 15px var(--primary-glow);\n" +
"    }\n" +
"    .input-bar input {\n" +
"      flex: 1;\n" +
"      background: transparent;\n" +
"      border: none;\n" +
"      outline: none;\n" +
"      color: var(--text);\n" +
"      font-size: 0.95rem;\n" +
"      font-family: var(--font-sans);\n" +
"    }\n" +
"    .btn-send {\n" +
"      background: var(--primary);\n" +
"      color: #0F172A;\n" +
"      border: none;\n" +
"      font-weight: 600;\n" +
"      padding: 0.65rem 1.15rem;\n" +
"      border-radius: 12px;\n" +
"      cursor: pointer;\n" +
"      transition: all 0.2s;\n" +
"      display: inline-flex;\n" +
"      align-items: center;\n" +
"      gap: 0.4rem;\n" +
"    }\n" +
"    .btn-send:hover {\n" +
"      background: #7DD3FC;\n" +
"    }\n" +
"    .btn-send:disabled {\n" +
"      opacity: 0.5;\n" +
"      cursor: not-allowed;\n" +
"    }\n" +
"    .loading-spinner {\n" +
"      display: inline-block;\n" +
"      width: 14px;\n" +
"      height: 14px;\n" +
"      border: 2px solid rgba(15, 23, 42, 0.3);\n" +
"      border-radius: 50%;\n" +
"      border-top-color: #0F172A;\n" +
"      animation: spin 0.8s linear infinite;\n" +
"    }\n" +
"    @keyframes spin { to { transform: rotate(360deg); } }\n" +
"  </style>\n" +
"</head>\n" +
"<body>\n" +
"  <header>\n" +
"    <div class=\"brand\">\n" +
"      <div class=\"avatar\">BK</div>\n" +
"      <div>\n" +
"        <div class=\"brand-title\">Bankesh Kumar — Career Assistant</div>\n" +
"        <div class=\"brand-subtitle\">Technical Lead | Mobile & Platform Architect (13+ Years)</div>\n" +
"      </div>\n" +
"    </div>\n" +
"    <div class=\"nav-actions\">\n" +
"      <a class=\"btn-outline\" href=\"https://bankeshportfolio.vercel.app/\" target=\"_blank\">🌐 Portfolio</a>\n" +
"      <a class=\"btn-outline\" href=\"https://github.com/bankeshkumar90/bjak-lead-engineer-bankesh\" target=\"_blank\">💻 GitHub</a>\n" +
"    </div>\n" +
"  </header>\n" +
"\n" +
"  <main>\n" +
"    <div>\n" +
"      <div class=\"chips-label\">Suggested Interview Questions:</div>\n" +
"      <div class=\"chips-row\">\n" +
"        <button class=\"chip\" onclick=\"ask('What is your current role and responsibilities at Wipro?')\">🏢 Current Role at Wipro</button>\n" +
"        <button class=\"chip\" onclick=\"ask('What did you build at ZoomCar for Zoom One?')\">🚗 ZoomCar & Zoom One</button>\n" +
"        <button class=\"chip\" onclick=\"ask('Tell me about your CabCharge white-label taxi app.')\">🚖 CabCharge Taxi App</button>\n" +
"        <button class=\"chip\" onclick=\"ask('What is your experience with Offline AI, Ollama, and local Qwen?')\">🤖 Offline AI & Ollama</button>\n" +
"        <button class=\"chip\" onclick=\"ask('What degree and IoT certification do you hold?')\">🎓 Education & IIT Delhi IoT</button>\n" +
"        <button class=\"chip\" onclick=\"ask('What is your engineering team leadership scope?')\">👥 Team Leadership (22 Engineers)</button>\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <div id=\"chatFeed\"></div>\n" +
"\n" +
"    <form class=\"input-bar\" onsubmit=\"handleSubmit(event)\">\n" +
"      <input id=\"queryInput\" type=\"text\" placeholder=\"Ask any technical question about Bankesh's experience...\" autocomplete=\"off\" />\n" +
"      <button id=\"sendBtn\" class=\"btn-send\" type=\"submit\">\n" +
"        <span>Ask</span>\n" +
"      </button>\n" +
"    </form>\n" +
"  </main>\n" +
"\n" +
"  <script>\n" +
"    const chatFeed = document.getElementById('chatFeed');\n" +
"    const queryInput = document.getElementById('queryInput');\n" +
"    const sendBtn = document.getElementById('sendBtn');\n" +
"\n" +
"    // Welcome message on load\n" +
"    window.addEventListener('DOMContentLoaded', () => {\n" +
"      appendAssistantCard({\n" +
"        answer: \"Hello! I am the AI Career Assistant representing Bankesh Kumar. Every answer I provide is strictly grounded in Bankesh's verified CV, architecture records, and portfolio (https://bankeshportfolio.vercel.app/).\\n\\nFeel free to ask about his 13+ years of experience leading teams, building mobile platforms (React Native, Android, iOS, KMM), backend microservices in Spring Boot, or offline AI agents!\",\n" +
"        status: 'READY',\n" +
"        executionMode: 'VERIFIED_GROUNDED_ENGINE',\n" +
"        latencyMs: 0,\n" +
"        sourceReferences: ['Official CV (data/cv.md)', 'Portfolio Site (bankeshportfolio.vercel.app)']\n" +
"      });\n" +
"    });\n" +
"\n" +
"    function ask(text) {\n" +
"      queryInput.value = text;\n" +
"      handleSubmit(new Event('submit'));\n" +
"    }\n" +
"\n" +
"    async function handleSubmit(e) {\n" +
"      if (e) e.preventDefault();\n" +
"      const question = queryInput.value.trim();\n" +
"      if (!question) return;\n" +
"\n" +
"      appendUserBubble(question);\n" +
"      queryInput.value = '';\n" +
"      setLoading(true);\n" +
"\n" +
"      try {\n" +
"        const res = await fetch('/api/ask', {\n" +
"          method: 'POST',\n" +
"          headers: { 'Content-Type': 'application/json' },\n" +
"          body: JSON.stringify({ question })\n" +
"        });\n" +
"        const data = await res.json();\n" +
"        appendAssistantCard(data);\n" +
"      } catch (err) {\n" +
"        appendAssistantCard({\n" +
"          answer: 'Error communicating with assistant backend: ' + err.message,\n" +
"          status: 'ERROR',\n" +
"          executionMode: 'NETWORK_ERROR',\n" +
"          latencyMs: 0\n" +
"        });\n" +
"      } finally {\n" +
"        setLoading(false);\n" +
"      }\n" +
"    }\n" +
"\n" +
"    function appendUserBubble(text) {\n" +
"      const div = document.createElement('div');\n" +
"      div.className = 'msg msg-user';\n" +
"      div.innerHTML = `<div class=\"bubble\">${escapeHtml(text)}</div>`;\n" +
"      chatFeed.appendChild(div);\n" +
"      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });\n" +
"    }\n" +
"\n" +
"    function appendAssistantCard(data) {\n" +
"      const div = document.createElement('div');\n" +
"      div.className = 'msg msg-assistant';\n" +
"\n" +
"      const isSuccess = data.status === 'SUCCESS' || data.status === 'READY';\n" +
"      const badgeClass = isSuccess ? 'badge-success' : 'badge-refusal';\n" +
"      const badgeText = data.status || 'PROCESSED';\n" +
"\n" +
"      let sourcesHtml = '';\n" +
"      if (data.sourceReferences && data.sourceReferences.length > 0) {\n" +
"        sourcesHtml = `\n" +
"          <div class=\"card-sources\">\n" +
"            <div class=\"sources-title\">Verified Sources & Citations:</div>\n" +
"            ${data.sourceReferences.map(s => {\n" +
"              const match = s.match(/^\\[(FACT-\\d{3})\\]\\s*(.*)/);\n" +
"              if (match) {\n" +
"                return `<div class=\"source-item\"><span class=\"source-tag\">[${match[1]}]</span><span>${escapeHtml(match[2])}</span></div>`;\n" +
"              }\n" +
"              return `<div class=\"source-item\">• ${escapeHtml(s)}</div>`;\n" +
"            }).join('')}\n" +
"          </div>\n" +
"        `;\n" +
"      }\n" +
"\n" +
"      let auditHtml = '';\n" +
"      if (data.auditTrail && data.auditTrail.length > 0) {\n" +
"        const auditId = 'audit_' + Math.random().toString(36).substring(2, 9);\n" +
"        auditHtml = `\n" +
"          <div>\n" +
"            <span class=\"audit-toggle\" onclick=\"toggleAudit('${auditId}')\">🔍 Inspect Pipeline Audit Trail</span>\n" +
"            <div id=\"${auditId}\" class=\"audit-box\">\n" +
"              ${data.auditTrail.map(a => `<div>&gt; ${escapeHtml(a)}</div>`).join('')}\n" +
"            </div>\n" +
"          </div>\n" +
"        `;\n" +
"      }\n" +
"\n" +
"      div.innerHTML = `\n" +
"        <div class=\"card\">\n" +
"          <div class=\"card-meta\">\n" +
"            <span class=\"badge ${badgeClass}\">● ${badgeText}</span>\n" +
"            <span>Mode: <strong>${data.executionMode || 'N/A'}</strong> | Latency: <strong>${data.latencyMs || 0}ms</strong></span>\n" +
"          </div>\n" +
"          <div class=\"card-body\">${escapeHtml(data.answer)}</div>\n" +
"          ${sourcesHtml}\n" +
"          ${auditHtml}\n" +
"        </div>\n" +
"      `;\n" +
"      chatFeed.appendChild(div);\n" +
"      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });\n" +
"    }\n" +
"\n" +
"    function toggleAudit(id) {\n" +
"      const el = document.getElementById(id);\n" +
"      if (el) el.style.display = (el.style.display === 'block') ? 'none' : 'block';\n" +
"    }\n" +
"\n" +
"    function setLoading(isLoading) {\n" +
"      sendBtn.disabled = isLoading;\n" +
"      sendBtn.innerHTML = isLoading ? '<span class=\"loading-spinner\"></span>' : '<span>Ask</span>';\n" +
"    }\n" +
"\n" +
"    function escapeHtml(s) {\n" +
"      if (!s) return '';\n" +
"      return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');\n" +
"    }\n" +
"  </script>\n" +
"</body>\n" +
"</html>\n";
    }
}
