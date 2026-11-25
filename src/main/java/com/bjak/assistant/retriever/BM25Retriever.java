package com.bjak.assistant.retriever;

import com.bjak.assistant.model.Fact;
import com.bjak.assistant.store.FactStore;
import com.bjak.assistant.store.SQLiteIndex;

import java.util.*;

/**
 * Retrieves top-k facts using BM25 scoring with metadata filtering and heuristic intent boosting.
 */
public class BM25Retriever {
    private final FactStore factStore;
    private final SQLiteIndex index;

    public BM25Retriever(FactStore factStore, SQLiteIndex index) {
        this.factStore = factStore;
        this.index = index;
    }

    public static class RetrievalResult {
        private final List<Fact> facts;
        private final boolean confident;
        private final double topScore;

        public RetrievalResult(List<Fact> facts, boolean confident, double topScore) {
            this.facts = facts;
            this.confident = confident;
            this.topScore = topScore;
        }

        public List<Fact> getFacts() { return facts; }
        public boolean isConfident() { return confident; }
        public double getTopScore() { return topScore; }
    }

    public RetrievalResult retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return new RetrievalResult(Collections.emptyList(), false, 0.0);
        }

        List<SQLiteIndex.FactScore> rawScores = index.search(query, topK * 2);
        Map<String, Double> scoreMap = new HashMap<>();

        for (SQLiteIndex.FactScore fs : rawScores) {
            scoreMap.put(fs.fact.getId(), fs.score);
        }

        // Apply metadata filters & intent boosts
        applyMetadataBoosts(query.toLowerCase(), scoreMap);

        // Sort by final boosted scores
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scoreMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Fact> resultFacts = new ArrayList<>();
        double highestScore = sorted.isEmpty() ? 0.0 : sorted.get(0).getValue();

        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            if (entry.getValue() > 0.05) {
                Fact f = factStore.getFact(entry.getKey());
                if (f != null) {
                    resultFacts.add(f);
                }
            }
        }

        // If no facts were matched by exact terms but the query is in-scope or asking about Bankesh,
        // provide the foundational overview facts so general inquiries receive comprehensive answers
        if (resultFacts.isEmpty()) {
            String lower = query.toLowerCase();
            if (lower.contains("bankesh") || lower.contains("about") || lower.contains("who") ||
                lower.contains("tell") || lower.contains("summary") || lower.contains("profile") ||
                lower.contains("experience") || lower.contains("work")) {
                List<String> defaultIds = List.of("FACT-001", "FACT-014", "FACT-021", "FACT-013", "FACT-006");
                for (String id : defaultIds) {
                    Fact f = factStore.getFact(id);
                    if (f != null && !resultFacts.contains(f)) {
                        resultFacts.add(f);
                    }
                }
                highestScore = 5.0;
            }
        }

        // Confident if we have facts and score >= 0.05
        boolean confident = !resultFacts.isEmpty() && highestScore >= 0.05;
        return new RetrievalResult(resultFacts, confident, highestScore);
    }

    private void applyMetadataBoosts(String query, Map<String, Double> scoreMap) {
        // === CORE ROLES, EMPLOYERS & TIMELINE ===
        // Dataset 01: Overview / Introduction / About Bankesh
        boostIfAny(scoreMap, query, 3.5, List.of("FACT-001", "FACT-014", "FACT-021", "FACT-013", "FACT-006"),
            "about bankesh", "who is bankesh", "who is", "who are you", "tell me about bankesh", "tell me", "summary", "overview", "profile", "background", "intro", "bankesh", "yourself");

        // Dataset 02: Current Role at Wipro (June 2024 - Present)
        boostIfAny(scoreMap, query, 5.0, "FACT-001",
            "current", "present", "wipro", "technical lead", "wipro role", "current company");

        // Dataset 03: Lead Engineer & Architect at ZoomCar (Nov 2023 - Mar 2024)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-005", "FACT-006"),
            "zoomcar", "zoom car", "zoomcar india", "car rental platform");

        // Dataset 04: Senior Software Engineer & Architect at Tekion (Mar 2022 - Jun 2023)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-007", "FACT-008"),
            "tekion", "tekion india", "automotive dealership", "dealership platform");

        // Dataset 05: Senior Software Engineer & Lead at Zibal Tech (Apr 2020 - Feb 2022)
        boostIfAny(scoreMap, query, 5.0, "FACT-009",
            "zibal", "zibal tech", "zibal tech pvt");

        // Dataset 06: Senior Software Engineer at Quarks TechnoSoft (Feb 2019 - Feb 2020)
        boostIfAny(scoreMap, query, 5.0, "FACT-010",
            "quarks", "quarks technosoft", "quarks techno");

        // Dataset 07: Senior Software Engineer at Triazine Software (2013 - 2018)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "triazine", "triazine software", "triazine solutions");

        // Dataset 08: Android Developer Early Career at SpearHead (2013 - 2015)
        boostIfAny(scoreMap, query, 5.0, "FACT-012",
            "spearhead", "early career", "junior developer", "first job", "2013-2015");

        // Dataset 09: Career Transition Timeline from ZoomCar to Wipro (Mar 2024 - Jun 2024)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-001", "FACT-019"),
            "transition", "timeline", "zoomcar to wipro", "march 2024", "june 2024", "gap");

        // === FLAGSHIP PRODUCTION PROJECTS ===
        // Dataset 10: CabCharge Automotive White-Label Taxi Platform (Wipro)
        boostIfAny(scoreMap, query, 5.5, "FACT-002",
            "cabcharge", "taxi", "taxi app", "taxi booking", "white label", "white-label", "automotive taxi");

        // Dataset 11: Aviva Cross-Platform Insurance & Wellbeing Platform (Wipro)
        boostIfAny(scoreMap, query, 5.5, "FACT-003",
            "aviva", "insurance app", "insurance", "wellbeing app", "wellbeing", "wellness tracking", "claims management");

        // Dataset 12: Zoom One Unified Car Rental Platform (ZoomCar)
        boostIfAny(scoreMap, query, 5.5, List.of("FACT-005", "FACT-006"),
            "zoom one", "zoomone", "unified platform", "guest and host", "car rental", "rental app", "rental apps", "rental");

        // Dataset 13: OTT TV Set-Top-Box Digital Display Platform (Tekion)
        boostIfAny(scoreMap, query, 5.5, List.of("FACT-007", "FACT-008"),
            "ott tv", "android tv", "set-top-box", "digital display", "lounge view", "dealership tv");

        // Dataset 14: Set-Top-Box Heartbeat & Firmware ADB Bridge (Tekion)
        boostIfAny(scoreMap, query, 5.0, "FACT-008",
            "firmware bridge", "heartbeat", "system app", "remote wi-fi", "firewall detection", "hdmi customization");

        // Dataset 15: StrataGem CMMI Level 5 Multiplayer Brain Game (Zibal Tech)
        boostIfAny(scoreMap, query, 5.0, "FACT-009",
            "stratagem", "brain-training", "multiplayer game", "game modes", "trivia game", "jumble");

        // Dataset 16: N-Radius Dual-Role On-Demand Food Delivery Platform (Zibal Tech)
        boostIfAny(scoreMap, query, 5.0, "FACT-009",
            "n-radius", "food delivery", "delivery agent", "dual-role", "restaurant app");

        // Dataset 17: SmartCat Machine Learning Data Collection Tool (Zibal Tech)
        boostIfAny(scoreMap, query, 5.0, "FACT-009",
            "smartcat", "ml data", "data collection tool", "training dataset", "metadata collection");

        // Dataset 18: AdviceFit Gym & Fitness Membership Management Platform (Quarks)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-010", "FACT-017"),
            "advicefit", "gym management", "fitness app", "spa management", "wellness center");

        // Dataset 19: Residential Housing Society & Security Management App (Quarks)
        boostIfAny(scoreMap, query, 5.0, "FACT-010",
            "housing society", "residential society", "visitor management", "society security");

        // Dataset 20: Ticket-Pro US Civic Compliance & POS Violation Enforcement (Triazine)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "ticket-pro", "ticket pro", "civic compliance", "civic violations", "us administration");

        // Dataset 21: Traffic-Pro US Police Law Enforcement Traffic Citations (Triazine)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "traffic-pro", "traffic pro", "law enforcement", "police officer", "traffic violations");

        // Dataset 22: Hello Tax Indian Income Tax ITR-IV Filing Platform (Triazine)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "hello tax", "itr", "income tax", "tax filing", "itr-iv", "tax platform");

        // Dataset 23: Domino's Pizza Mobile App & AWS Payment Integration (Triazine)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-011", "FACT-017"),
            "domino's", "dominos", "pizza app", "domino pizza");

        // Dataset 24: Rajasthan Government DOIT Citizen Apps (eMitra, Sampark, Rajdhara) (Triazine)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-011", "FACT-017"),
            "rajasthan", "emitra", "rajasthan sampark", "rajdhara", "doit department", "social security app");

        // Dataset 25: Jaunt Fix & Twerck Chat Social Networking Platform (Triazine)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "jaunt fix", "twerck", "travel social", "vendor discovery", "photo sharing chat");

        // Dataset 26: SpearHead Educational & Entertainment Apps (Smart-Kidz, Khel-Cafe)
        boostIfAny(scoreMap, query, 5.0, "FACT-012",
            "smart-kidz", "khel-cafe", "e-books", "pdf reader app", "general knowledge app");

        // === MOBILE PLATFORMS & FRAMEWORKS ===
        // Dataset 27: React Native Cross-Platform Mobile Development
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-002", "FACT-006", "FACT-021"),
            "react native", "react-native", "react native developer", "rn app");

        // Dataset 28: Android Native SDK & Kotlin Android Development
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-021"),
            "android sdk", "native android", "android architecture", "android developer");

        // Dataset 29: iOS Native SDK & Swift Mobile Development
        boostIfAny(scoreMap, query, 5.0, "FACT-021",
            "ios sdk", "swift", "native ios", "xcode", "apple platform");

        // Dataset 30: Kotlin Multiplatform (KMM / KMP) Shared Codebases
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-006", "FACT-021"),
            "kotlin multiplatform", "kmm", "kmp", "multiplatform mobile", "shared business logic");

        // Dataset 31: Jetpack Compose Modern Declarative Android UI
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-006", "FACT-021"),
            "jetpack compose", "compose ui", "declarative ui");

        // Dataset 32: Tamagui Universal UI System & Cross-Platform Styling
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-002", "FACT-003"),
            "tamagui", "design system", "universal ui", "cross-platform styling");

        // Dataset 33: React Native Bridge & Native Module Interoperability
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-006", "FACT-021"),
            "react native bridge", "native bridge", "native modules", "phased rollout");

        // Dataset 34: IBM WorkLight & MobileFirst Enterprise Hybrid Apps
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "worklight", "ibm worklight", "mobilefirst", "ibm mobilefirst");

        // Dataset 35: Apache Cordova & Adobe PhoneGap Hybrid Architectures
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-011", "FACT-021"),
            "cordova", "phonegap", "hybrid mobile");

        // === STATE MANAGEMENT & ASYNCHRONOUS DATA FLOW ===
        // Dataset 36: Redux & Redux Toolkit (RTK) Centralized State Management
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-002", "FACT-006"),
            "redux", "redux toolkit", "redux-toolkit", "state management");

        // Dataset 37: RTK Query Asynchronous Data Fetching & Caching
        boostIfAny(scoreMap, query, 5.0, "FACT-002",
            "rtk query", "rtk-query", "data fetching", "cache management");

        // Dataset 38: Redux-Saga Generator-Based Middleware & Side Effect Sync
        boostIfAny(scoreMap, query, 5.0, "FACT-009",
            "redux-saga", "redux saga", "sagas", "side effect management");

        // Dataset 39: Fast High-Performance Key-Value Storage (MMKV)
        boostIfAny(scoreMap, query, 5.0, "FACT-002",
            "mmkv", "fast storage", "key-value storage", "tencent mmkv");

        // Dataset 40: Room Database & SQLite Mobile Persistence Layer
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-009"),
            "room database", "room persistence", "android room", "sqlite orm");

        // Dataset 41: Kotlin Coroutines & Flow Asynchronous Concurrency
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-021"),
            "coroutines", "kotlin coroutines", "kotlin flow", "async concurrency");

        // Dataset 42: Android LiveData & Reactive Streams (RxKotlin / RxJava)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-021"),
            "livedata", "rxkotlin", "rxjava", "reactive stream");

        // Dataset 43: Shared ViewModel & APIVM Architectural Layer Unification
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "viewmodel", "apivm", "shared viewmodel", "data duplication elimination");

        // === DEPENDENCY INJECTION, NETWORKING & APIS ===
        // Dataset 44: Hilt & Dagger Compile-Time Dependency Injection
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-006", "FACT-021"),
            "hilt", "dagger", "dependency injection", "di framework");

        // Dataset 45: Retrofit Type-Safe HTTP Client for Android & Kotlin
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-021"),
            "retrofit", "http client", "rest client android");

        // Dataset 46: Axios HTTP Client for React Native Networking
        boostIfAny(scoreMap, query, 5.0, "FACT-002",
            "axios", "http requests", "networking react native");

        // Dataset 47: KTOR Asynchronous Multiplatform HTTP Client
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "ktor", "ktor client", "multiplatform networking");

        // Dataset 48: GraphQL APIs, Schemas, Queries & Real-Time Subscriptions
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-006"),
            "graphql", "graphql api", "apollo", "graphql schema");

        // Dataset 49: RESTful APIs Design, Headers, JSON Serialization & HTTP Verbs
        boostIfAny(scoreMap, query, 4.5, List.of("FACT-004", "FACT-006"),
            "rest api", "rest apis", "restful", "json payload");

        // Dataset 50: WebSocket Real-Time Full-Duplex Bidirectional Messaging
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-009"),
            "websocket", "web sockets", "real-time messaging", "bidirectional stream");

        // Dataset 51: XMPP Protocol & eJabberd Real-Time Chat Infrastructure
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "xmpp", "ejabberd", "chat server", "instant messaging protocol");

        // === BACKEND, CLOUD & MICROSERVICES ===
        // Dataset 52: Java Spring Boot Enterprise Microservices Development
        boostIfAny(scoreMap, query, 5.5, "FACT-004",
            "spring boot", "springboot", "java backend", "spring microservices");

        // Dataset 53: Netflix Eureka Service Registry & Dynamic Service Discovery
        boostIfAny(scoreMap, query, 5.5, "FACT-004",
            "eureka", "service registry", "service discovery", "eureka server");

        // Dataset 54: Spring Cloud API Gateway & Centralized Request Routing
        boostIfAny(scoreMap, query, 5.5, "FACT-004",
            "api gateway", "routing gateway", "spring cloud gateway", "centralized routing");

        // Dataset 55: Spring Cloud Config Server & Centralized Property Management
        boostIfAny(scoreMap, query, 5.5, "FACT-004",
            "config server", "spring config", "centralized configuration", "cloud config");

        // Dataset 56: Node.js, Express & Server-Side JavaScript Backends
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "node.js", "nodejs", "express.js", "backend prototyping", "nodemon");

        // Dataset 57: MongoDB & Mongoose NoSQL Schemas & Database Operations
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "mongodb", "mongoose", "nosql", "document database");

        // Dataset 58: Docker Containerization for Backend Services & AI Workflows
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-004", "FACT-013"),
            "docker", "container", "containers", "dockerfile", "containerized");

        // Dataset 59: Amazon Web Services (AWS) Cloud Services & Infrastructure
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-008", "FACT-011"),
            "aws", "amazon web services", "cloud hosting", "aws gateway");

        // === HARDWARE, IOT & DEVICE INTEGRATION ===
        // Dataset 60: MQTT IoT Real-Time Lightweight Messaging Broker
        boostIfAny(scoreMap, query, 5.5, "FACT-008",
            "mqtt", "iot broker", "lightweight messaging", "bi-directional hardware");

        // Dataset 61: RFID Scanner Hardware & Tag Communicator Integration
        boostIfAny(scoreMap, query, 5.5, "FACT-008",
            "rfid", "rfid communicator", "rfid scanner", "rfid tag");

        // Dataset 62: ExoPlayer Video Playback Engine & Dealership Streaming
        boostIfAny(scoreMap, query, 5.5, "FACT-008",
            "exoplayer", "video playback", "video streaming", "live streaming android");

        // Dataset 63: Android Debug Bridge (ADB), HDMI Customization & Device Reboot
        boostIfAny(scoreMap, query, 5.0, "FACT-008",
            "adb", "hdmi", "device reboot", "set-top-box bridge", "hardware control");

        // Dataset 64: Point-of-Sale (POS) Terminals, MSR & Bluetooth Thermal Printers
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "pos device", "msr", "magnetic stripe", "bluetooth printer", "thermal printer");

        // Dataset 65: Automotive Dealership Digital Display & Real-Time Repair Tracking
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-007", "FACT-008"),
            "dealership display", "repair order tracking", "service lounge", "customer display");

        // === BIOMETRICS, SECURITY & COMPLIANCE ===
        // Dataset 66: Morpho & SecuGen Fingerprint Biometric Scanner SDKs
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "morpho", "secugen", "fingerprint scanner", "biometric reader");

        // Dataset 67: IRIS Scanner Biometric Hardware Authentication
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "iris scanner", "iris sdk", "biometric auth", "biometrics");

        // Dataset 68: UIDAI Aadhaar Verification & 3-Layer Data Encryption
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "aadhaar", "uidai", "3-layer encryption", "aadhaar api", "biometric authentication");

        // Dataset 69: EMV Chip & Payment Gateway Processing (PayU, AWS)
        boostIfAny(scoreMap, query, 5.0, "FACT-011",
            "emv", "emv chip", "payu", "payment gateway");

        // Dataset 70: Enterprise Client Confidentiality & NDA Protection
        boostIfAny(scoreMap, query, 6.0, "FACT-020",
            "confidentiality", "confidential", "nda", "proprietary", "client secrecy", "source code", "financial profits", "internal source code");

        // === AI, LLMS & AUTOMATION ===
        // Dataset 71: Local Privacy-First Offline AI Agents & RAG Workflows
        boostIfAny(scoreMap, query, 5.5, "FACT-013",
            "offline ai", "local ai", "privacy-first ai", "air-gapped ai", "on-device ai");

        // Dataset 72: Alibaba Qwen LLMs (Qwen 3 8B, Local Open Weights)
        boostIfAny(scoreMap, query, 5.5, "FACT-013",
            "qwen", "qwen 3", "qwen 8b", "qwen 3 8b", "alibaba qwen");

        // Dataset 73: Ollama Local LLM Runtime & Model Serving Engine
        boostIfAny(scoreMap, query, 5.5, "FACT-013",
            "ollama", "local llm", "self-hosted llm", "local model server");

        // Dataset 74: N8n Node-Based Workflow Automation & Agent Orchestration
        boostIfAny(scoreMap, query, 5.5, "FACT-013",
            "n8n", "workflow automation", "automation tooling", "automation pipeline");

        // Dataset 75: AI Forward-Deployed Engineering (FDE) & Agentic Mode
        boostIfAny(scoreMap, query, 5.5, List.of("FACT-013", "FACT-014"),
            "forward deployed", "forward-deployed", "fde", "agentic", "agentic workflows", "ai agents");

        // === LEADERSHIP, TESTING & PROFESSIONAL CREDENTIALS ===
        // Dataset 76: Engineering Team Leadership & Mentorship (up to 22 Engineers)
        boostIfAny(scoreMap, query, 5.0, "FACT-014",
            "22 engineers", "team size", "team lead", "engineering leadership", "mentoring", "cross-functional team");

        // Dataset 77: CMMI Level 5 & Level 3 Process Governance & Quality Standards
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-009", "FACT-011", "FACT-014"),
            "cmmi", "cmmi level 5", "cmmi level 3", "process governance", "process maturity");

        // Dataset 78: 30+ Production Applications Across 8+ Industry Domains
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-014", "FACT-021"),
            "30+ apps", "30 applications", "8+ domains", "8 domains", "industry domains");

        // Dataset 79: Agile, Scrum Sprint Planning & Delivery Ownership
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-014", "FACT-003"),
            "agile", "scrum", "sprint planning", "delivery ownership", "jira", "release management");

        // Dataset 80: Clean Architecture, MVVM, MVI & High/Low Level Design (HLD/LLD)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-021", "FACT-002", "FACT-004", "FACT-006"),
            "clean architecture", "mvvm", "mvi", "hld", "lld", "high level design", "low level design", "architectures");

        // Dataset 81: Automated Unit & Integration Testing (Jest, JUnit, Mockito)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-002", "FACT-008", "FACT-021"),
            "junit", "mockito", "jest", "unit testing", "integration testing", "test automation");

        // Dataset 82: CI/CD Pipeline Automation (Jenkins, Azure DevOps, Bitbucket)
        boostIfAny(scoreMap, query, 5.0, List.of("FACT-002", "FACT-009", "FACT-014"),
            "jenkins", "azure devops", "ado", "ci/cd", "continuous integration", "bitbucket");

        // Dataset 83: Deep-Linking & Inter-App Navigation (MyWorkplace, DW Wealth)
        boostIfAny(scoreMap, query, 5.5, "FACT-003",
            "deep linking", "deep-linking", "myworkplace", "dw wealth", "inter-app navigation");

        // Dataset 84: SonarQube Code Quality, Stability & Redundancy Reduction
        boostIfAny(scoreMap, query, 5.5, List.of("FACT-002", "FACT-003"),
            "sonarqube", "code quality", "quality and stability", "stability", "static analysis", "code smells", "code coverage");

        // Dataset 85: Google Maps & Places API Geospatial Navigation & Routing
        boostIfAny(scoreMap, query, 5.5, "FACT-002",
            "google maps", "places api", "route visualization", "geofencing", "location search");

        // Dataset 86: CleverTap User Behavioral Analytics & AI Team Funnels
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "clevertap", "user analytics", "behavioral tracking", "funnel optimization");

        // Dataset 87: Firebase Crashlytics Real-Time Telemetry & Stability
        boostIfAny(scoreMap, query, 5.0, "FACT-006",
            "crashlytics", "crash reporting", "telemetry", "stability monitoring");

        // Dataset 88: Bachelor of Technology (B.Tech IT) Degree (80% Aggregate)
        boostIfAny(scoreMap, query, 5.5, "FACT-015",
            "b.tech", "btech", "degree", "information technology", "80%", "bachelor", "university", "college", "education");

        // Dataset 89: IIT Delhi Internet of Things (IoT) Professional Certification
        boostIfAny(scoreMap, query, 5.5, "FACT-016",
            "iit delhi", "iit", "iot", "internet of things", "certification", "certified");

        // Dataset 90: Published Applications on Google Play Store & Apple App Store
        boostIfAny(scoreMap, query, 5.5, "FACT-017",
            "published", "play store", "app store", "published apps", "google play", "apple app store");

        // Dataset 91: Candidate Location, Contact Info & Professional Profiles
        boostIfAny(scoreMap, query, 5.5, "FACT-018",
            "contact", "email", "phone", "location", "bangalore", "bengaluru", "varthur", "linkedin", "github");
    }

    private void boostIfAny(Map<String, Double> scoreMap, String query, double delta, String factId, String... terms) {
        for (String term : terms) {
            if (query.contains(term)) {
                boost(scoreMap, factId, delta);
                return;
            }
        }
    }

    private void boostIfAny(Map<String, Double> scoreMap, String query, double delta, List<String> factIds, String... terms) {
        for (String term : terms) {
            if (query.contains(term)) {
                for (String fid : factIds) {
                    boost(scoreMap, fid, delta);
                }
                return;
            }
        }
    }

    private void boost(Map<String, Double> scoreMap, String factId, double delta) {
        if (factStore.getFact(factId) != null) {
            scoreMap.put(factId, scoreMap.getOrDefault(factId, 0.0) + delta);
        }
    }
}
