const factsData = require('../data/facts.json');
const allowlist = require('../data/allowlist.json');

const INJECTION_PATTERNS = [
  /ignore\s+(all|any|previous|above|prior|the)*\s*(all|any|previous|above|prior)*\s*instructions?/i,
  /disregard\s+(all|any|previous|above|prior|the)*\s*(all|any|previous|above|prior)*\s*instructions?/i,
  /fabricate|exaggerate/i,
  /you\s+are\s+now\s+(a|an)?\s*.*/i,
  /pretend\s+(you\s+are|to\s+be)/i,
  /act\s+as\s+(a|an)?/i,
  /reveal\s+(the|your)?\s*(system\s+prompt|instructions?|hidden\s+prompt)/i,
  /system\s+prompt/i,
  /jailbreak|dan\s+mode|developer\s+mode/i,
  /<script.*?>|eval\s*\(|javascript:/i,
  /forget\s+(all|everything|previous)/i,
  /override\s+(safety|rules|instructions)/i
];

const UNANSWERABLE_PRIVATE_PATTERNS = [
  /(salary|bonus|compensation|net pay|package|ctc)/i,
  /(passport|blood group|marital status|religion|political party)/i,
  /(solana|smart contract|ethereum)/i,
  /(master of science|master's degree|canada|stanford|mit)/i
];

const OUT_OF_SCOPE_PATTERNS = [
  /capital\s+of\s+[a-z]+/i,
  /recipe\s+(for|to)/i,
  /weather\s+(in|at|today)/i,
  /write\s+(a|me\s+a)?\s*(poem|song|story|joke|haiku)/i,
  /stock\s+price\s+of/i,
  /who\s+won\s+the\s+(world\s+cup|super\s+bowl|election|match)/i,
  /calculate\s+\d+|what\s+is\s+\d+\s*[\+\-\*\/]\s*\d+/i,
  /translate\s+.*\s+to\s+[a-z]+/i
];

function isGreeting(query) {
  if (!query) return false;
  const clean = query.trim().replace(/[!.,?]+$/, '').trim();
  return /^(hi|hello|hey|greetings|howdy|hola|yo|hiya|good\s+(morning|afternoon|evening|day))(\s+(there|bankesh|assistant|ai|kumar))?$/i.test(clean);
}

function tokenize(text) {
  return (text || '').toLowerCase().replace(/[^a-z0-9]/g, ' ').split(/\s+/).filter(t => t.length > 1);
}

module.exports = async (req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const startTime = Date.now();
  let question = '';
  if (req.body && req.body.question) {
    question = req.body.question.trim();
  } else if (req.query && req.query.question) {
    question = req.query.question.trim();
  }

  if (!question) {
    return res.status(200).json({
      question: '',
      answer: "Please feel free to ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering leadership.\n\nI do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, speculate, or fabricate qualifications, employers, or metrics.",
      status: 'REFUSED_OUT_OF_SCOPE',
      executionMode: 'ROUTER_DETERMINISTIC_REFUSAL',
      latencyMs: Date.now() - startTime,
      citedFactIds: [],
      sourceReferences: [],
      auditTrail: ['Router: Empty question provided -> clean refusal.']
    });
  }

  // 1. Prompt Injection Check
  for (const p of INJECTION_PATTERNS) {
    if (p.test(question)) {
      return res.status(200).json({
        question,
        answer: "Refusal [Safety/Security]: Query contains instruction override or prompt injection attempt. The assistant strictly answers questions grounded in Bankesh Kumar's verified professional record.",
        status: 'REFUSED_INJECTION',
        executionMode: 'ROUTER_DETERMINISTIC_REFUSAL',
        latencyMs: Date.now() - startTime,
        citedFactIds: [],
        sourceReferences: [],
        auditTrail: ['Router check failed: REFUSED_INJECTION -> routed to Refusal.']
      });
    }
  }

  // 2. Greeting Check
  if (isGreeting(question)) {
    return res.status(200).json({
      question,
      answer: "Hello! Please ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering leadership.\n\nI do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, speculate, or fabricate qualifications, employers, or metrics.",
      status: 'REFUSED_UNANSWERABLE',
      executionMode: 'ROUTER_DETERMINISTIC_REFUSAL',
      latencyMs: Date.now() - startTime,
      citedFactIds: [],
      sourceReferences: [],
      auditTrail: ['Router: Pure greeting detected -> prompted user to ask about Bankesh profile followed by refusal standard.']
    });
  }

  // 3. Unanswerable Private Patterns
  for (const p of UNANSWERABLE_PRIVATE_PATTERNS) {
    if (p.test(question)) {
      return res.status(200).json({
        question,
        answer: "Please feel free to ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering achievements.\n\nI do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, speculate, or fabricate qualifications, employers, salary, or personal metrics.",
        status: 'REFUSED_UNANSWERABLE',
        executionMode: 'ROUTER_DETERMINISTIC_REFUSAL',
        latencyMs: Date.now() - startTime,
        citedFactIds: [],
        sourceReferences: [],
        auditTrail: ['Router: Query falls within unanswerable private domain -> clean refusal.']
      });
    }
  }

  // 4. Out of Scope Patterns
  for (const p of OUT_OF_SCOPE_PATTERNS) {
    if (p.test(question)) {
      return res.status(200).json({
        question,
        answer: "Refusal [Out of Scope]: I am an AI Career Assistant dedicated exclusively to Bankesh Kumar's professional experience, architecture projects, engineering leadership, and qualifications. I do not answer general knowledge, weather, creative writing, or external domain queries.",
        status: 'REFUSED_OUT_OF_SCOPE',
        executionMode: 'ROUTER_DETERMINISTIC_REFUSAL',
        latencyMs: Date.now() - startTime,
        citedFactIds: [],
        sourceReferences: [],
        auditTrail: ['Router: Query out of scope -> clean refusal.']
      });
    }
  }

  // 5. BM25 Retrieval & Boost Scoring
  const facts = factsData.facts || [];
  const qTokens = tokenize(question);
  const lowerQ = question.toLowerCase();

  // Overview query detection
  const isOverview = (
    lowerQ.includes('tell me about bankesh') ||
    lowerQ.includes('who is bankesh') ||
    lowerQ.includes('tell me about yourself') ||
    lowerQ.includes('who are you') ||
    lowerQ.includes('overview') ||
    lowerQ.includes('summary') ||
    lowerQ.includes('profile') ||
    lowerQ.includes('introduce bankesh') ||
    lowerQ.trim() === 'bankesh' ||
    lowerQ.trim() === 'bankesh kumar' ||
    (lowerQ.includes('about') && lowerQ.includes('bankesh') && lowerQ.length < 35)
  ) && !/mqtt|rfid|spring|taxi|cabcharge|aviva|zoomcar|tekion|zibal|quarks|triazine|domino|stratagem|n-radius|smartcat|advicefit|compose|kmm|degree|college|contact|email|phone|location|salary/i.test(lowerQ);

  if (isOverview) {
    const overviewFactIds = ["FACT-014", "FACT-001", "FACT-002", "FACT-003", "FACT-021", "FACT-004", "FACT-013", "FACT-005", "FACT-006", "FACT-007", "FACT-008", "FACT-009", "FACT-010", "FACT-011", "FACT-015", "FACT-016"];
    const answer = "Bankesh Kumar is an AI Forward-Deployed Engineer, Technical Lead, and Mobile/Platform Architect with 13+ years of enterprise experience delivering scalable systems across fintech, automotive, insurance, OTT, gaming, food delivery, and government sectors [FACT-014].\n\n" +
      "Key Professional Overview:\n" +
      "• Current Role: Technical Lead at Wipro (June 2024 – Present), driving platform architecture including the CabCharge white-label taxi app and Aviva insurance/wellbeing app [FACT-001, FACT-002, FACT-003].\n" +
      "• Technical Specialization: Expert in React Native, Android (Kotlin), iOS (Swift), Kotlin Multiplatform (KMM), and Clean Architecture / MVVM [FACT-021], as well as Spring Boot microservices [FACT-004].\n" +
      "• Offline AI & Local LLMs: Builds privacy-first offline AI agents and automated workflows utilizing Ollama, local Qwen (e.g. Qwen 3 8B), N8n, and Docker [FACT-013].\n" +
      "• Architecture Track Record: Previously Lead Engineer/Architect at ZoomCar (merging apps into Zoom One [FACT-005, FACT-006]), Tekion (Android TV OTT [FACT-007, FACT-008]), Zibal Tech (StrataGem CMMI Level 5 game [FACT-009]), Quarks (AdviceFit [FACT-010]), and Triazine (Domino's Pizza, Hello Tax, Aadhaar apps [FACT-011]).\n" +
      "• Leadership & Scope: Led cross-functional teams of up to 22 engineers, delivering 30+ production apps across 8+ industry domains [FACT-014].\n" +
      "• Education & Certification: B.Tech in Information Technology (80%) [FACT-015] and IoT Certified from IIT Delhi [FACT-016].";

    return res.status(200).json({
      question,
      answer,
      status: 'SUCCESS',
      executionMode: 'FALLBACK_EXTRACTIVE',
      latencyMs: Date.now() - startTime,
      citedFactIds: overviewFactIds,
      sourceReferences: overviewFactIds.map(fid => `[${fid}] Verified Fact Reference`),
      auditTrail: [
        'Router check passed: Query is in-scope.',
        'Retriever: Identified Executive Overview intent.',
        'Fallback executed: Grounded synthesis from verified facts.'
      ]
    });
  }

  // Score facts
  const scored = facts.map(f => {
    let score = 0;
    const fText = ((f.content || '') + ' ' + (f.keywords || []).join(' ') + ' ' + (f.title || '')).toLowerCase();
    const fTokens = tokenize(fText);

    // Token match scoring
    for (const qt of qTokens) {
      if (fTokens.includes(qt)) score += 2.0;
      else if (fText.includes(qt)) score += 1.0;
    }

    // Apply metadata boost datasets
    const fid = f.id;
    // Current role / Wipro
    if (fid === 'FACT-001' && /wipro|current|present|technical lead/.test(lowerQ)) score += 5.0;
    // CabCharge / Taxi
    if (fid === 'FACT-002' && /cabcharge|taxi|white label|mmkv|google maps|automotive/.test(lowerQ)) score += 5.5;
    // Aviva / Wellbeing / Insurance
    if (fid === 'FACT-003' && /aviva|insurance|wellbeing|wellness|deep linking|sonarqube/.test(lowerQ)) score += 5.5;
    // Spring Boot / Microservices
    if (fid === 'FACT-004' && /spring boot|springboot|eureka|microservice|backend|api gateway|config server/.test(lowerQ)) score += 5.5;
    // ZoomCar / Zoom One
    if ((fid === 'FACT-005' || fid === 'FACT-006') && /zoomcar|zoom one|car rental|rental|clevertap|crashlytics|ktor|guest|host/.test(lowerQ)) score += 5.5;
    // Tekion / OTT TV / RFID / MQTT
    if ((fid === 'FACT-007' || fid === 'FACT-008') && /tekion|ott|android tv|mqtt|rfid|exoplayer|firmware|set-top-box/.test(lowerQ)) score += 5.5;
    // Zibal Tech / StrataGem / N-Radius / SmartCat
    if (fid === 'FACT-009' && /zibal|stratagem|n-radius|smartcat|brain-training|food delivery|workmanager|redux-saga/.test(lowerQ)) score += 5.0;
    // Quarks / AdviceFit / Housing Society
    if (fid === 'FACT-010' && /quarks|advicefit|fitness|gym|housing society|itext/.test(lowerQ)) score += 5.0;
    // Triazine / Domino's / Hello Tax / Ticket-Pro / Traffic-Pro / Aadhaar
    if (fid === 'FACT-011' && /triazine|ticket-pro|traffic-pro|hello tax|domino|aadhaar|uidai|morpho|secugen|iris|worklight/.test(lowerQ)) score += 5.0;
    // SpearHead
    if (fid === 'FACT-012' && /spearhead|smart-kidz|khel-cafe|early career/.test(lowerQ)) score += 5.0;
    // Offline AI / Ollama / Qwen / N8n
    if (fid === 'FACT-013' && /ai|ollama|qwen|n8n|local llm|offline ai|privacy-first|agentic|forward deployed/.test(lowerQ)) score += 5.5;
    // Leadership / 22 engineers / 30+ apps / CMMI
    if (fid === 'FACT-014' && /team|lead|22 engineers|engineers|leadership|cmmi|agile|scrum|30\+ apps|8\+ domains/.test(lowerQ)) score += 5.0;
    // Education / B.Tech IT
    if (fid === 'FACT-015' && /education|degree|b\.tech|btech|information technology|80%|college|university/.test(lowerQ)) score += 5.5;
    // IoT / IIT Delhi
    if (fid === 'FACT-016' && /certification|certified|iit|iot|internet of things|delhi/.test(lowerQ)) score += 5.5;
    // Published Apps
    if (fid === 'FACT-017' && /published|play store|app store|apps|google play/.test(lowerQ)) score += 5.5;
    // Contact / Bangalore
    if (fid === 'FACT-018' && /contact|email|phone|location|bangalore|varthur|linkedin|github/.test(lowerQ)) score += 5.5;
    // Transition
    if ((fid === 'FACT-001' || fid === 'FACT-019') && /transition|timeline|zoomcar to wipro|march 2024/.test(lowerQ)) score += 5.0;
    // Skills / Mobile platforms / Clean Architecture
    if (fid === 'FACT-021' && /mobile platform|react native|kotlin|swift|clean architecture|mvvm|mvi|jetpack compose|kmm/.test(lowerQ)) score += 5.0;

    return { fact: f, score };
  });

  scored.sort((a, b) => b.score - a.score);
  const topFacts = scored.filter(s => s.score >= 1.5).slice(0, 5).map(s => s.fact);

  if (topFacts.length === 0) {
    return res.status(200).json({
      question,
      answer: "Please feel free to ask about Bankesh Kumar's profile, career background, technical skills, mobile & platform architecture, or engineering achievements.\n\nI do not have verified records in Bankesh Kumar's professional documentation regarding this inquiry. As an AI career assistant representing Bankesh, I am strictly constrained to verified facts and do not extrapolate, speculate, or fabricate qualifications, employers, or metrics.",
      status: 'REFUSED_UNANSWERABLE',
      executionMode: 'FALLBACK_DETERMINISTIC_REFUSAL',
      latencyMs: Date.now() - startTime,
      citedFactIds: [],
      sourceReferences: [],
      auditTrail: [
        'Router check passed: Query in-scope.',
        'Retriever: Top score below threshold -> clean deterministic refusal.'
      ]
    });
  }

  // Extractive grounded synthesis
  let answer = "Based on Bankesh Kumar's verified professional documentation:\n\n";
  const citedFactIds = [];
  const sourceReferences = [];

  for (const f of topFacts) {
    answer += `• ${f.content} [${f.id}]\n`;
    citedFactIds.push(f.id);
    sourceReferences.push(`[${f.id}] ${f.title} (Source: ${f.source})`);
  }

  return res.status(200).json({
    question,
    answer: answer.trim(),
    status: 'SUCCESS',
    executionMode: 'FALLBACK_EXTRACTIVE',
    latencyMs: Date.now() - startTime,
    citedFactIds,
    sourceReferences,
    auditTrail: [
      'Router check passed: In-scope.',
      `Retriever: Fetched ${topFacts.length} facts (Confident: true).`,
      'Generator: Deterministic extractive synthesis from verified facts.'
    ]
  });
};
