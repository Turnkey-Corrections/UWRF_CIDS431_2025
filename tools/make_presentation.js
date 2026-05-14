const fs = require("fs");
const path = require("path");

const outDir = path.resolve(__dirname, "..", "presentation_build");
const pptxName = "UWRF_CIDS431_Lecture_Quiz_Generator_Presentation.pptx";

const W = 13.333;
const H = 7.5;
const EMU = 914400;

const colors = {
  ink: "17201D",
  muted: "5E6D68",
  bg: "F5F7F8",
  white: "FFFFFF",
  green: "196F5F",
  greenDark: "105246",
  greenSoft: "DDEDEA",
  blue: "315F8C",
  blueSoft: "DFEAF4",
  orange: "B65C2E",
  orangeSoft: "F4E3D7",
  yellowSoft: "FBF7EF",
  border: "D8E0DD",
  red: "B42318",
};

const slides = [
  {
    eyebrow: "UWRF CIDS 431 Final Project",
    title: "Lecture Video Quiz Generator",
    subtitle: "A serverless AWS pipeline that turns lecture media or transcripts into multiple-choice quiz JSON.",
    footer: "Aaron Klein | May 14, 2026",
    type: "title",
  },
  {
    title: "Project Goal",
    subtitle: "Build something more complex than a basic web app by combining managed cloud services into a useful workflow.",
    bullets: [
      "Input: lecture video in S3 or transcript text from a browser.",
      "Processing: Lambda coordinates Transcribe, Bedrock, and storage.",
      "Output: reusable quiz JSON with source metadata and generated questions.",
      "Value: demonstrates serverless architecture, AI integration, IAM, infrastructure as code, and front-end workflow design.",
    ],
    callout: "Core idea: upload learning content once, then automatically create study questions from it.",
  },
  {
    title: "Requirement Fit",
    subtitle: "The project goes beyond EC2, RDS, S3-only storage, Glacier, Docker, and a simple web app.",
    type: "table",
    rows: [
      ["Requirement", "How this project addresses it"],
      ["Cloud services beyond basics", "Uses Lambda, API Gateway, Transcribe, Bedrock, IAM, CloudFormation/CDK, and S3 events."],
      ["Replicable project", "CDK defines infrastructure in Java; Maven builds Lambda artifact; README documents setup and commands."],
      ["Presentation-ready progress", "Includes deployable backend, browser UI, local transcript sample, unit tests, and generated cloud template."],
      ["Job-search relevance", "Shows infrastructure as code, serverless event processing, AI API use, least-privilege thinking, and testing."],
    ],
  },
  {
    title: "Architecture",
    subtitle: "Two entry paths feed one quiz-generation workflow.",
    type: "architecture",
  },
  {
    title: "AWS Infrastructure",
    subtitle: "Defined in Java CDK inside UwrfStack.java.",
    cards: [
      ["S3 video bucket", "Private bucket named from studentName; stores videos, transcripts, and quiz JSON."],
      ["VideoHandler Lambda", "Triggered by .mp4 or transcript objects; starts Transcribe when needed and writes quiz output."],
      ["TranscriptApiHandler Lambda", "Receives browser POST requests and reads generated quizzes through GET polling."],
      ["API Gateway", "Exposes /quiz with CORS for the browser tool."],
      ["IAM policies", "Grant S3 read/write, Transcribe job actions, Bedrock InvokeModel, and model subscription visibility."],
      ["CDK outputs", "Print bucket name and Quiz API URL after deployment."],
    ],
  },
  {
    title: "Processing Flow",
    subtitle: "The backend supports both full video processing and faster transcript-based demos.",
    type: "timeline",
    steps: [
      ["1", "Upload", "A video lands in S3, or the browser uploads transcript text through API Gateway."],
      ["2", "Trigger", "S3 ObjectCreated events invoke the Java Lambda handlers."],
      ["3", "Transcribe", "For media files, AWS Transcribe writes transcript JSON under transcripts/."],
      ["4", "Generate", "Bedrock Claude 3 Haiku receives a prompt and returns 10 MCQ questions as JSON."],
      ["5", "Store & review", "Lambda writes quizzes/<source>-quiz.json; the browser polls and renders the result."],
    ],
  },
  {
    title: "Implementation Details",
    subtitle: "Key code paths and design choices.",
    bullets: [
      "VideoHandler detects .mp4, .txt, and .json transcript objects and normalizes all paths into transcript text.",
      "BedrockQuizGenerator calls anthropic.claude-3-haiku-20240307-v1:0 using the Bedrock Runtime InvokeModel API.",
      "MockQuizGenerator allows local testing without Bedrock cost or AWS credentials.",
      "TranscriptApiHandler validates request body, limits transcript size to 120,000 characters, writes S3 transcript objects, and supports GET polling.",
      "Maven shade builds target/lambda.jar for both Lambda handlers.",
    ],
  },
  {
    title: "Browser Demo UI",
    subtitle: "The web folder provides a focused transcript upload and quiz review experience.",
    type: "ui",
  },
  {
    title: "Testing & Validation",
    subtitle: "Local tests prove handler behavior before deploying to AWS.",
    cards: [
      ["6 passing tests", "mvn '-Dmaven.repo.local=.m2/repository' test completed with 6 tests, 0 failures, 0 errors."],
      ["S3 event simulation", "VideoHandlerTest creates fake ObjectCreated events for single and multiple records."],
      ["API request simulation", "TranscriptApiHandlerTest verifies POST upload metadata, bad request handling, and GET quiz retrieval."],
      ["Cost control", "MOCK_BEDROCK=true keeps local and video-handler development from invoking paid model calls."],
    ],
    callout: "Validation strategy: test locally first, package with Maven, then use CDK synth/diff/deploy for the real AWS run.",
  },
  {
    title: "Challenges & Fixes",
    subtitle: "Problems encountered and how the project handles them.",
    type: "challenges",
    rows: [
      ["Long video processing", "Lambda can time out while waiting for Transcribe.", "Set a 15-minute timeout and check remaining time during polling."],
      ["Bedrock cost/model access", "Real AI calls require model access and can cost money.", "Use MockQuizGenerator during development and switch MOCK_BEDROCK=false for final AI tests."],
      ["Browser async workflow", "Quiz generation is not instant after upload.", "Return expected S3 key, then poll API Gateway until the quiz exists or timeout occurs."],
      ["Unsafe public storage", "Lecture files and generated quizzes should not be public by default.", "Block all public S3 access and expose controlled API reads."],
    ],
  },
  {
    title: "What Was Accomplished",
    subtitle: "Current working project state.",
    bullets: [
      "A Java CDK stack creates the core cloud infrastructure.",
      "Two Java Lambda handlers support S3 event processing and browser-driven transcript upload.",
      "Transcribe and Bedrock integration paths are implemented.",
      "The browser app can upload a TXT transcript, wait for generated quiz JSON, render questions, and download the result.",
      "Sample transcript artifacts are present under transcripts/ for repeatable demos.",
      "Automated unit tests pass locally.",
    ],
  },
  {
    title: "Conclusion",
    subtitle: "What this project demonstrates about cloud computing.",
    type: "conclusion",
    bullets: [
      "Managed services can replace always-on servers for event-driven workloads.",
      "Infrastructure as Code makes cloud setup repeatable and reviewable.",
      "AI services are most useful when wrapped in a clear workflow, validation, and cost controls.",
      "The project connects storage, compute, APIs, IAM, and front-end UX into one portfolio-ready system.",
    ],
    callout: "Final takeaway: this is a realistic serverless AI workflow, not just a static app or single-service demo.",
  },
];

function esc(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function emu(v) {
  return Math.round(v * EMU);
}

function sp(id, name, x, y, w, h, opts = {}) {
  const fill = opts.fill ? `<a:solidFill><a:srgbClr val="${opts.fill}"/></a:solidFill>` : "<a:noFill/>";
  const line = opts.line ? `<a:ln w="${opts.lineWidth || 12700}"><a:solidFill><a:srgbClr val="${opts.line}"/></a:solidFill></a:ln>` : "<a:ln><a:noFill/></a:ln>";
  const radius = opts.radius ? "roundRect" : (opts.shape || "rect");
  return `<p:sp><p:nvSpPr><p:cNvPr id="${id}" name="${esc(name)}"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="${emu(x)}" y="${emu(y)}"/><a:ext cx="${emu(w)}" cy="${emu(h)}"/></a:xfrm><a:prstGeom prst="${radius}"><a:avLst/></a:prstGeom>${fill}${line}</p:spPr>${opts.text ? txBody(opts.text, opts) : ""}</p:sp>`;
}

function tx(id, name, x, y, w, h, text, opts = {}) {
  return `<p:sp><p:nvSpPr><p:cNvPr id="${id}" name="${esc(name)}"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr><a:xfrm><a:off x="${emu(x)}" y="${emu(y)}"/><a:ext cx="${emu(w)}" cy="${emu(h)}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>${txBody(text, opts)}</p:sp>`;
}

function txBody(text, opts = {}) {
  const paras = Array.isArray(text) ? text : [text];
  const anchor = opts.valign === "mid" ? "ctr" : (opts.valign || "t");
  const align = opts.align || "l";
  const font = opts.font || "Aptos";
  const sz = Math.round((opts.size || 18) * 100);
  const color = opts.color || colors.ink;
  const bold = opts.bold ? ' b="1"' : "";
  const bodyPr = `<a:bodyPr wrap="square" anchor="${anchor}" lIns="${opts.padL || 91440}" tIns="${opts.padT || 45720}" rIns="${opts.padR || 91440}" bIns="${opts.padB || 45720}"/>`;
  return `<p:txBody>${bodyPr}<a:lstStyle/>${paras.map((p) => para(p, { align, font, sz, color, bold })).join("")}</p:txBody>`;
}

function para(text, opts) {
  return `<a:p><a:pPr algn="${opts.align}"/><a:r><a:rPr lang="en-US" sz="${opts.sz}" dirty="0"${opts.bold}><a:solidFill><a:srgbClr val="${opts.color}"/></a:solidFill><a:latin typeface="${opts.font}"/></a:rPr><a:t>${esc(text)}</a:t></a:r><a:endParaRPr lang="en-US" sz="${opts.sz}"/></a:p>`;
}

function bulletList(startId, items, x, y, w, size = 19) {
  let xml = "";
  items.forEach((item, i) => {
    xml += sp(startId + i * 2, `bullet dot ${i}`, x, y + i * 0.55 + 0.09, 0.14, 0.14, { fill: i % 2 ? colors.orange : colors.green, shape: "ellipse" });
    xml += tx(startId + i * 2 + 1, `bullet ${i}`, x + 0.28, y + i * 0.55, w - 0.28, 0.44, item, { size, color: colors.ink });
  });
  return xml;
}

function header(slide, idStart = 10) {
  let xml = tx(idStart, "title", 0.55, 0.35, 8.6, 0.45, slide.title, { size: 26, bold: true, color: colors.ink, padL: 0 });
  xml += tx(idStart + 1, "subtitle", 0.58, 0.88, 9.6, 0.35, slide.subtitle || "", { size: 13.5, color: colors.muted, padL: 0 });
  xml += sp(idStart + 2, "header rule", 0.55, 1.32, 12.2, 0.02, { fill: colors.border });
  return xml;
}

function titleSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg });
  xml += sp(21, "left band", 0, 0, 0.22, H, { fill: colors.green });
  xml += sp(22, "accent", 0.22, 0, 0.08, H, { fill: colors.orange });
  xml += tx(23, "eyebrow", 0.75, 1.15, 5.8, 0.35, slide.eyebrow, { size: 14, bold: true, color: colors.orange, padL: 0 });
  xml += tx(24, "main title", 0.75, 1.62, 6.8, 1.35, slide.title, { size: 42, bold: true, color: colors.ink, padL: 0 });
  xml += tx(25, "subtitle", 0.78, 3.05, 6.5, 0.86, slide.subtitle, { size: 20, color: colors.muted, padL: 0 });
  xml += sp(26, "architecture panel", 7.75, 1.0, 4.75, 4.6, { fill: colors.white, line: colors.border, radius: true });
  const boxes = [
    ["S3", colors.greenSoft, 8.15, 1.45],
    ["Lambda", colors.blueSoft, 9.9, 1.45],
    ["Transcribe", colors.orangeSoft, 8.15, 2.9],
    ["Bedrock", colors.blueSoft, 9.9, 2.9],
    ["Quiz JSON", colors.greenSoft, 9.02, 4.35],
  ];
  boxes.forEach(([label, fill, x, y], i) => {
    xml += sp(30 + i, label, x, y, 1.35, 0.68, { fill, line: colors.border, radius: true, text: label, size: 15, bold: true, align: "c", valign: "mid", color: colors.ink });
  });
  xml += sp(40, "flow arrow 1", 9.48, 1.66, 0.32, 0.22, { fill: colors.orange, shape: "rightArrow" });
  xml += sp(41, "flow arrow 2", 8.66, 2.22, 0.22, 0.52, { fill: colors.orange, shape: "downArrow" });
  xml += sp(42, "flow arrow 3", 9.48, 3.1, 0.32, 0.22, { fill: colors.orange, shape: "rightArrow" });
  xml += sp(43, "flow arrow 4", 10.33, 3.68, 0.22, 0.52, { fill: colors.orange, shape: "downArrow" });
  xml += tx(50, "footer", 0.78, 6.65, 5.5, 0.32, slide.footer, { size: 12.5, color: colors.muted, padL: 0 });
  return xml;
}

function tableSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  const x = 0.72, y = 1.65, w1 = 3.15, w2 = 8.75, rowH = 0.86;
  slide.rows.forEach((row, i) => {
    const fill = i === 0 ? colors.green : (i % 2 ? colors.white : "F9FBFA");
    const textColor = i === 0 ? colors.white : colors.ink;
    xml += sp(40 + i * 4, "table left", x, y + i * rowH, w1, rowH, { fill, line: colors.border });
    xml += sp(41 + i * 4, "table right", x + w1, y + i * rowH, w2, rowH, { fill, line: colors.border });
    xml += tx(42 + i * 4, "table text left", x + 0.08, y + i * rowH + 0.1, w1 - 0.16, rowH - 0.16, row[0], { size: i === 0 ? 14.5 : 13.5, bold: true, color: textColor });
    xml += tx(43 + i * 4, "table text right", x + w1 + 0.08, y + i * rowH + 0.1, w2 - 0.16, rowH - 0.16, row[1], { size: i === 0 ? 14.5 : 13.2, color: textColor, bold: i === 0 });
  });
  return xml;
}

function architectureSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  const nodes = [
    ["Browser UI", "Transcript .txt upload and quiz display", 0.7, 1.8, colors.blueSoft],
    ["API Gateway", "POST /quiz and GET /quiz?key=", 3.1, 1.8, colors.white],
    ["Transcript Lambda", "Writes transcripts/ object and reads quiz JSON", 5.55, 1.8, colors.greenSoft],
    ["S3 Bucket", "videos, transcripts, quizzes", 8.2, 1.8, colors.yellowSoft],
    ["Video Lambda", "S3 event processor", 3.1, 4.1, colors.greenSoft],
    ["AWS Transcribe", "media to transcript JSON", 5.55, 4.1, colors.orangeSoft],
    ["AWS Bedrock", "Claude Haiku quiz generation", 8.2, 4.1, colors.blueSoft],
    ["Quiz JSON", "quizzes/<source>-quiz.json", 10.7, 2.95, colors.white],
  ];
  nodes.forEach(([t, d, x, y, fill], i) => {
    xml += sp(40 + i, t, x, y, 1.92, 0.92, { fill, line: colors.border, radius: true });
    xml += tx(60 + i * 2, t, x + 0.12, y + 0.12, 1.68, 0.25, t, { size: 12.5, bold: true, color: colors.ink, padL: 0 });
    xml += tx(61 + i * 2, d, x + 0.12, y + 0.42, 1.68, 0.35, d, { size: 9.6, color: colors.muted, padL: 0 });
  });
  const arrows = [
    [2.55, 2.12, 0.38, 0.22], [5.0, 2.12, 0.38, 0.22], [7.58, 2.12, 0.38, 0.22],
    [8.78, 2.83, 0.22, 0.9, "downArrow"], [5.0, 4.42, 0.38, 0.22], [7.58, 4.42, 0.38, 0.22],
    [10.15, 4.08, 0.35, 0.22], [10.15, 3.15, 0.38, 0.22],
  ];
  arrows.forEach(([x, y, w, h, shape], i) => xml += sp(100 + i, "arrow", x, y, w, h, { fill: colors.orange, shape: shape || "rightArrow" }));
  xml += tx(120, "note", 0.82, 6.45, 11.8, 0.35, "The system keeps storage private and uses APIs/events to move work between managed AWS services.", { size: 13, color: colors.muted, align: "c" });
  return xml;
}

function timelineSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  slide.steps.forEach(([n, title, desc], i) => {
    const x = 0.72 + i * 2.48;
    xml += sp(40 + i, "step card", x, 2.0, 2.05, 3.35, { fill: colors.white, line: colors.border, radius: true });
    xml += sp(50 + i, "step num", x + 0.18, 2.22, 0.48, 0.48, { fill: i % 2 ? colors.orange : colors.green, shape: "ellipse", text: n, size: 16, bold: true, color: colors.white, align: "c", valign: "mid" });
    xml += tx(60 + i, "step title", x + 0.22, 2.9, 1.62, 0.34, title, { size: 16, bold: true, color: colors.ink, padL: 0 });
    xml += tx(70 + i, "step desc", x + 0.22, 3.35, 1.62, 1.25, desc, { size: 12.2, color: colors.muted, padL: 0 });
    if (i < slide.steps.length - 1) xml += sp(90 + i, "flow arrow", x + 2.12, 3.43, 0.28, 0.22, { fill: colors.orange, shape: "rightArrow" });
  });
  return xml;
}

function cardsSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  const cols = slide.cards.length > 4 ? 3 : 2;
  const cardW = cols === 3 ? 3.82 : 5.75;
  slide.cards.forEach(([t, d], i) => {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const x = 0.72 + col * (cardW + 0.28);
    const y = 1.68 + row * 1.55;
    xml += sp(40 + i, "card", x, y, cardW, 1.25, { fill: colors.white, line: colors.border, radius: true });
    xml += sp(70 + i, "card bar", x, y, 0.08, 1.25, { fill: i % 2 ? colors.orange : colors.green });
    xml += tx(90 + i * 2, "card title", x + 0.22, y + 0.14, cardW - 0.42, 0.28, t, { size: 13.7, bold: true, color: colors.ink, padL: 0 });
    xml += tx(91 + i * 2, "card desc", x + 0.22, y + 0.48, cardW - 0.42, 0.48, d, { size: 10.8, color: colors.muted, padL: 0 });
  });
  if (slide.callout) xml += sp(150, "callout", 0.75, 6.42, 11.85, 0.48, { fill: colors.yellowSoft, line: "EAD8B8", radius: true, text: slide.callout, size: 13.2, bold: true, color: colors.ink });
  return xml;
}

function uiSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  xml += sp(40, "app shell", 0.85, 1.65, 11.65, 4.95, { fill: colors.white, line: colors.border, radius: true });
  xml += sp(41, "topbar", 1.12, 1.92, 11.1, 0.72, { fill: colors.greenSoft, line: colors.border, radius: true });
  xml += tx(42, "ui title", 1.35, 2.12, 4.5, 0.25, "Lecture Quiz Generator", { size: 15, bold: true, color: colors.ink, padL: 0 });
  xml += sp(43, "download button", 10.45, 2.08, 1.32, 0.34, { fill: colors.white, line: colors.border, radius: true, text: "Download JSON", size: 9.5, bold: true, align: "c", valign: "mid" });
  xml += sp(44, "controls", 1.12, 2.9, 11.1, 0.75, { fill: "FAFBFB", line: colors.border, radius: true });
  xml += sp(45, "api input", 1.35, 3.12, 4.4, 0.3, { fill: colors.white, line: colors.border, radius: true, text: "API Gateway URL", size: 9, color: colors.muted });
  xml += sp(46, "file btn", 6.05, 3.12, 1.7, 0.3, { fill: colors.white, line: colors.border, radius: true, text: "Choose TXT File", size: 9, bold: true, align: "c" });
  xml += sp(47, "upload btn", 10.25, 3.12, 1.55, 0.3, { fill: colors.green, radius: true, text: "Upload Transcript", size: 8.8, bold: true, color: colors.white, align: "c" });
  xml += sp(48, "status", 1.12, 3.86, 11.1, 0.56, { fill: colors.yellowSoft, line: "EAD8B8", radius: true, text: "Pipeline status: File -> S3 -> Lambda -> Quiz", size: 11.3, bold: true, color: colors.ink });
  xml += sp(49, "left panel", 1.12, 4.62, 5.35, 1.65, { fill: colors.white, line: colors.border, radius: true });
  xml += sp(50, "right panel", 6.85, 4.62, 5.35, 1.65, { fill: colors.white, line: colors.border, radius: true });
  xml += tx(51, "left title", 1.35, 4.85, 2.4, 0.25, "Transcript Preview", { size: 12, bold: true, color: colors.ink, padL: 0 });
  xml += tx(52, "right title", 7.08, 4.85, 2.4, 0.25, "Generated Quiz", { size: 12, bold: true, color: colors.ink, padL: 0 });
  xml += tx(53, "left copy", 1.35, 5.25, 4.65, 0.55, "User can inspect or edit transcript text before upload.", { size: 10.8, color: colors.muted, padL: 0 });
  xml += tx(54, "right copy", 7.08, 5.25, 4.65, 0.55, "Questions render with options and correct answers, then download as JSON.", { size: 10.8, color: colors.muted, padL: 0 });
  return xml;
}

function challengesSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  slide.rows.forEach(([issue, risk, fix], i) => {
    const y = 1.62 + i * 1.18;
    xml += sp(40 + i, "challenge", 0.75, y, 11.85, 0.94, { fill: colors.white, line: colors.border, radius: true });
    xml += tx(60 + i * 3, "issue", 1.0, y + 0.17, 2.15, 0.32, issue, { size: 13, bold: true, color: colors.ink, padL: 0 });
    xml += tx(61 + i * 3, "risk", 3.4, y + 0.16, 3.7, 0.38, risk, { size: 11.3, color: colors.muted, padL: 0 });
    xml += tx(62 + i * 3, "fix", 7.3, y + 0.16, 4.7, 0.38, fix, { size: 11.3, color: colors.greenDark, bold: true, padL: 0 });
  });
  return xml;
}

function contentSlide(slide) {
  let xml = sp(20, "background", 0, 0, W, H, { fill: colors.bg }) + header(slide);
  xml += bulletList(40, slide.bullets || [], 0.85, 1.8, 10.8, 18);
  if (slide.callout) xml += sp(150, "callout", 0.85, 6.18, 11.55, 0.62, { fill: colors.green, radius: true, text: slide.callout, size: 16, bold: true, color: colors.white, align: "c", valign: "mid" });
  return xml;
}

function slideXml(slide, idx) {
  let body;
  if (slide.type === "title") body = titleSlide(slide);
  else if (slide.type === "table") body = tableSlide(slide);
  else if (slide.type === "architecture") body = architectureSlide(slide);
  else if (slide.type === "timeline") body = timelineSlide(slide);
  else if (slide.type === "ui") body = uiSlide(slide);
  else if (slide.type === "challenges") body = challengesSlide(slide);
  else if (slide.cards) body = cardsSlide(slide);
  else body = contentSlide(slide);
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>${body}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>`;
}

function write(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, data, "utf8");
}

fs.rmSync(outDir, { recursive: true, force: true });
fs.mkdirSync(outDir, { recursive: true });

write(path.join(outDir, "[Content_Types].xml"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>${slides.map((_, i) => `<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>`).join("")}<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>`);

write(path.join(outDir, "_rels", ".rels"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>`);

write(path.join(outDir, "ppt", "presentation.xml"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"><p:sldIdLst>${slides.map((_, i) => `<p:sldId id="${256 + i}" r:id="rId${i + 1}"/>`).join("")}</p:sldIdLst><p:sldSz cx="${emu(W)}" cy="${emu(H)}" type="wide"/><p:notesSz cx="6858000" cy="9144000"/><p:defaultTextStyle/></p:presentation>`);

write(path.join(outDir, "ppt", "_rels", "presentation.xml.rels"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${slides.map((_, i) => `<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>`).join("")}</Relationships>`);

slides.forEach((slide, i) => {
  write(path.join(outDir, "ppt", "slides", `slide${i + 1}.xml`), slideXml(slide, i + 1));
});

write(path.join(outDir, "docProps", "core.xml"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>UWRF CIDS 431 Lecture Quiz Generator Presentation</dc:title><dc:creator>Aaron Klein</dc:creator><cp:lastModifiedBy>Codex</cp:lastModifiedBy><dcterms:created xsi:type="dcterms:W3CDTF">2026-05-14T03:50:00Z</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">2026-05-14T03:50:00Z</dcterms:modified></cp:coreProperties>`);

write(path.join(outDir, "docProps", "app.xml"), `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"><Application>Codex OpenXML Generator</Application><PresentationFormat>On-screen Show (16:9)</PresentationFormat><Slides>${slides.length}</Slides></Properties>`);

console.log(path.join(path.dirname(outDir), pptxName));
