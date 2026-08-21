import fs from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const OUT = new URL("../deliverables/", import.meta.url);
const RENDER = new URL("./rendered/", import.meta.url);
const W = 1280;
const H = 720;

const C = {
  ink: "#17202A",
  muted: "#5D6D7E",
  bg: "#F7F9F9",
  white: "#FFFFFF",
  navy: "#16324F",
  teal: "#009688",
  mint: "#D9F2EE",
  amber: "#F4B942",
  amberPale: "#FFF2D5",
  red: "#D8544F",
  redPale: "#FBE7E5",
  bluePale: "#E4EFF9",
  border: "#D7DEE4",
  green: "#2E8B57",
};

function shape(slide, geometry, left, top, width, height, fill = C.white, line = C.border, radius = undefined) {
  const s = slide.shapes.add({
    geometry,
    position: { left, top, width, height },
    fill,
    line: { style: "solid", fill: line, width: line === "none" ? 0 : 1 },
    ...(radius ? { borderRadius: radius } : {}),
  });
  return s;
}

function textBox(slide, text, left, top, width, height, opts = {}) {
  const s = slide.shapes.add({
    geometry: "textbox",
    position: { left, top, width, height },
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  s.text = text;
  s.text.style = {
    fontFamily: "Aptos",
    fontSize: opts.size ?? 24,
    bold: opts.bold ?? false,
    color: opts.color ?? C.ink,
    ...(opts.align ? { alignment: opts.align } : {}),
  };
  return s;
}

function header(slide, kicker, title, page) {
  slide.background.fill = C.bg;
  textBox(slide, kicker.toUpperCase(), 72, 42, 850, 26, { size: 15, bold: true, color: C.teal });
  textBox(slide, title, 72, 78, 1100, 62, { size: 47, bold: true, color: C.navy });
  shape(slide, "rect", 72, 150, 1136, 4, C.teal, "none");
  textBox(slide, `SAFE-NAVI  •  30% PROGRESS REVIEW`, 72, 682, 600, 20, { size: 12, bold: true, color: C.muted });
  textBox(slide, String(page).padStart(2, "0"), 1150, 682, 58, 20, { size: 12, bold: true, color: C.muted, align: "right" });
}

function panel(slide, left, top, width, height, title, body, tint = C.white, titleColor = C.navy) {
  shape(slide, "roundRect", left, top, width, height, tint, C.border, "rounded-xl");
  textBox(slide, title, left + 26, top + 24, width - 52, 38, { size: 25, bold: true, color: titleColor });
  textBox(slide, body, left + 26, top + 76, width - 52, height - 98, { size: 19, color: C.ink });
}

function note(slide, lines, sources = []) {
  const sourceBlock = sources.length ? `\n\n[Sources]\n${sources.map((s) => `- ${s}`).join("\n")}\n[/Sources]` : "";
  slide.speakerNotes.textFrame.setText(`${lines.join("\n\n")}${sourceBlock}`);
  slide.speakerNotes.setVisible(true);
}

function addCover(p) {
  const s = p.slides.add();
  s.background.fill = C.navy;
  shape(s, "rect", 72, 72, 10, 496, C.teal, "none");
  textBox(s, "COLLEGE PROJECT • MILESTONE 1", 112, 78, 850, 30, { size: 17, bold: true, color: C.amber });
  textBox(s, "Safe-Navi", 112, 148, 980, 92, { size: 82, bold: true, color: C.white });
  textBox(s, "30% Progress Review", 112, 246, 980, 72, { size: 49, bold: true, color: C.mint });
  textBox(s, "Community safety + intelligent navigation\nfor Mumbai and Navi Mumbai", 112, 350, 780, 104, { size: 29, color: C.white });
  shape(s, "roundRect", 930, 350, 240, 154, C.teal, "none", "rounded-xl");
  textBox(s, "WORKING\nPROTOTYPE", 958, 377, 184, 90, { size: 27, bold: true, color: C.white, align: "center" });
  textBox(s, "Presented by: Aniket Nikam & Team  •  August 2026", 112, 610, 940, 28, { size: 18, color: "#D5E4EE" });
  textBox(s, "Synthetic safety data • Real OSM road routes", 112, 650, 940, 24, { size: 15, color: "#AFC7D8" });
  note(s, [
    "Open by stating that Safe-Navi adds an explainable safety objective to conventional navigation.",
    "Clarify that this is the first 30% of the complete research and product roadmap: it is runnable, but uses synthetic safety data and is not a safety guarantee.",
  ], ["Project repository README.md"]);
}

function addProblem(p) {
  const s = p.slides.add(); header(s, "01 • Problem & proposal", "From reports to route decisions", 2);
  panel(s, 72, 190, 536, 400, "The gap", "• Fastest is not always preferred\n\n• Citizen reports may be unverified\n\n• Safety information is rarely connected to route choice\n\n• Missing data can create false reassurance", C.redPale, C.red);
  panel(s, 672, 190, 536, 400, "Safe-Navi's loop", "1. Citizen submits a location report\n\n2. Government user reviews it\n\n3. Verified hazard becomes structured data\n\n4. Real road alternatives are evaluated\n\n5. User chooses fastest, balanced, or safest", C.mint, C.green);
  note(s, [
    "Explain the two-part problem: route choice ignores local hazard context, while unverified reports cannot be treated as official facts.",
    "The design separates CitizenReport from Hazard. Only an authorized verification workflow creates an official hazard used with greater authority by risk and routing.",
  ], ["Project repository docs/IMPLEMENTATION_PLAN.md"]);
}

function addThirty(p) {
  const s = p.slides.add(); header(s, "02 • Data milestone", "City-scale synthetic dataset completed", 3);
  textBox(s, "30%", 72, 190, 300, 150, { size: 108, bold: true, color: C.teal });
  textBox(s, "Dataset + runnable\nfeasibility prototype", 76, 350, 330, 90, { size: 27, bold: true, color: C.navy });
  panel(s, 430, 190, 240, 170, "452,466", "physical OSM road segments", C.white);
  panel(s, 694, 190, 240, 170, "1,809,864", "segment–time rows", C.bluePale);
  panel(s, 958, 190, 250, 170, "10 × 4", "factors × time periods", C.amberPale);
  shape(s, "roundRect", 430, 390, 778, 162, C.mint, C.border, "rounded-xl");
  textBox(s, "Quality checks passed", 456, 416, 320, 34, { size: 25, bold: true, color: C.green });
  textBox(s, "No null factors • valid geometries • spatial ML split • balanced labels", 456, 466, 700, 58, { size: 19, color: C.ink });
  textBox(s, "Integrated now: 2.2 MB SQLite/RTree runtime  •  6,508 area/time rows  •  FastAPI  •  Android dataset scoring", 72, 605, 1136, 52, { size: 18, bold: true, color: C.muted });
  note(s, [
    "Do not measure progress only by the number of screens. The complete denominator includes data governance, secure backend, ML validation, operations, production hosting, and cross-platform delivery.",
    "The separate Mumbai–Navi Mumbai dataset is complete: 452,466 physical road segments become 1,809,864 rows across four time periods, with exactly ten synthetic factors.",
    "The Android app now queries a compact SQLite/RTree runtime containing all 6,508 one-kilometre area/time aggregates through FastAPI. The full Parquet remains the street-level training and future PostGIS source.",
  ], ["Dataset manifest.json", "Dataset QUALITY_REPORT.json", "Project repository docs/30_PERCENT_PROGRESS_REPORT.md"]);
}

function addCurrentDemo(p) {
  const s = p.slides.add(); header(s, "03 • Current demonstration", "What works today", 4);
  panel(s, 72, 188, 350, 420, "Citizen view", "• OpenStreetMap-derived map\n\n• Address or coordinate input\n\n• Driving, walking, cycling\n\n• Fastest / balanced / safest\n\n• Local score and explanations\n\n• Submit synthetic report", C.mint, C.teal);
  panel(s, 465, 188, 350, 420, "Government view", "• Review pending report\n\n• Verify and publish hazard\n\n• Downgrade to monitoring\n\n• Resolve active hazard\n\n• Role checks\n\n• Append-only audit history", C.bluePale, C.navy);
  panel(s, 858, 188, 350, 420, "Shared domain", "• Structured categories\n\n• Multiple geometry types\n\n• Severity and lifecycle\n\n• Duplicate matching\n\n• Route exposure\n\n• Synthetic-data label", C.amberPale, "#9A6500");
  note(s, [
    "Walk through the citizen and government sides as one connected lifecycle.",
    "Emphasize that routing uses genuine road geometry returned by Valhalla. Synthetic data applies only to hazard labels in this milestone.",
    "The mobile app does not scan the 158 MB Parquet file directly. It queries the complete dataset-derived area-cell runtime and clearly displays Dataset API, time period, coverage and leading factors.",
  ], ["Project repository README.md", "https://github.com/valhalla/valhalla/blob/master/README.md"]);
}

function addStack(p) {
  const s = p.slides.add(); header(s, "04 • Technology choices", "Open, explainable, and practical", 5);
  const rows = [
    ["Android Java + XML", "Reuse the stable mobile foundation and validate the idea quickly"],
    ["MapLibre + OpenStreetMap", "Provider-neutral interactive mapping without a Google Maps key"],
    ["Nominatim", "Resolve explicitly submitted place names to coordinates"],
    ["Valhalla", "Real driving, walking, and cycling routes plus alternatives"],
    ["FastAPI + SQLite/RTree", "Shareable dataset service with indexed point and route queries"],
    ["JUnit + Pytest", "Repeatable Android and backend verification before any ML claim"],
  ];
  rows.forEach((r, i) => {
    const y = 183 + i * 75;
    shape(s, "roundRect", 72, y, 1136, 58, i % 2 ? C.white : C.bluePale, C.border, "rounded-lg");
    textBox(s, r[0], 96, y + 14, 330, 30, { size: 20, bold: true, color: C.navy });
    textBox(s, r[1], 440, y + 14, 730, 30, { size: 18, color: C.ink });
  });
  note(s, [
    "Explain each technology in terms of the project decision it enables, not as a list of brand names.",
    "Nominatim is used only for explicit submissions, not autocomplete, and requests are rate-limited. Public services are for light demonstrations only.",
  ], [
    "https://github.com/maplibre/maplibre-native/releases",
    "https://openfreemap.org/quick_start/",
    "https://operations.osmfoundation.org/policies/nominatim/",
    "https://github.com/valhalla/valhalla/blob/master/docs/docs/api/openapi.yaml",
  ]);
}

function addRisk(p) {
  const s = p.slides.add(); header(s, "05 • Core method", "Explainable risk before machine learning", 6);
  const xs = [72, 356, 640, 924];
  const titles = ["10-factor data", "SQLite lookup", "Route exposure", "Profile ranking"];
  const bodies = [
    "Traffic, crime, lighting, density, roads, activity, access, flood and isolation",
    "RTree finds the current 1 km cell and time period",
    "Sample real candidate geometry and average dataset risk",
    "Fastest = time\nBalanced = both\nSafest = exposure priority",
  ];
  xs.forEach((x, i) => {
    panel(s, x, 218, 240, 300, titles[i], bodies[i], i === 3 ? C.mint : C.white, i === 3 ? C.green : C.navy);
    if (i < 3) textBox(s, "→", x + 244, 335, 36, 45, { size: 35, bold: true, color: C.teal, align: "center" });
  });
  shape(s, "roundRect", 170, 552, 940, 64, C.navy, "none", "rounded-lg");
  textBox(s, "Dataset risk API drives routing  •  Explainable hazard engine remains the labelled offline fallback", 195, 569, 890, 30, { size: 20, bold: true, color: C.white, align: "center" });
  note(s, [
    "State clearly: this milestone does not contain a trained ML model. The rule engine is a transparent baseline because the project does not yet have a lawful, labelled, real-world training set.",
    "The API returns route coverage and leading factor values. If it is unavailable, the UI explicitly says offline fallback rather than pretending dataset scoring occurred.",
  ], ["Project repository README.md", "Project repository app/src/main/java/com/safenavi/app/safety/risk"]);
}

function addEvidence(p) {
  const s = p.slides.add(); header(s, "06 • Verification evidence", "Built, tested, and repeatable", 7);
  const stats = [
    ["13", "Android JVM tests", C.teal],
    ["3", "backend API tests", C.navy],
    ["3", "route preferences", "#9A6500"],
  ];
  stats.forEach((st, i) => {
    const x = 72 + i * 388;
    shape(s, "roundRect", x, 196, 350, 210, i === 0 ? C.mint : i === 1 ? C.bluePale : C.amberPale, C.border, "rounded-xl");
    textBox(s, st[0], x + 24, 216, 302, 104, { size: 76, bold: true, color: st[2], align: "center" });
    textBox(s, st[1], x + 30, 326, 290, 42, { size: 22, bold: true, color: C.ink, align: "center" });
  });
  panel(s, 72, 444, 1136, 156, "Build gates passed", "Android lint ✓     Unit tests ✓     Debug APK assembly ✓     Route/geocoder parsers ✓     Role and lifecycle rules ✓", C.white, C.green);
  note(s, [
    "Thirteen Android tests cover risk, confidence, authorization, lifecycle, duplicates, map parsing and dataset-response parsing. Three backend tests verify health, metadata, point risk and profile scoring.",
    "Android lint, unit-test, and debug APK assembly tasks pass. Device UI automation remains future work.",
  ], ["Project repository app/src/test", "Local verified build output app/build/outputs/apk/debug/app-debug.apk"]);
}

function addRoadmap(p) {
  const s = p.slides.add(); header(s, "07 • Delivery roadmap", "From prototype to validated system", 8);
  const stages = [
    ["NOW • 30%", "Integrated MVP", "Android demo\nOSM routing\nSQLite + FastAPI", C.teal],
    ["NEXT • 55%", "Street precision", "PostGIS segments\nSecure roles\nPersistence + tests", C.navy],
    ["THEN • 80%", "Validation", "ML baselines\nBias + calibration\nHosted map stack", "#9A6500"],
    ["FINAL • 100%", "Productization", "Flutter Android/iOS\nAdmin + privacy\nPilot + monitoring", C.red],
  ];
  stages.forEach((st, i) => {
    const x = 72 + i * 284;
    shape(s, "roundRect", x, 215, 254, 350, C.white, C.border, "rounded-xl");
    shape(s, "rect", x, 215, 254, 10, st[3], "none");
    textBox(s, st[0], x + 20, 246, 214, 26, { size: 16, bold: true, color: st[3] });
    textBox(s, st[1], x + 20, 292, 214, 42, { size: 27, bold: true, color: C.navy });
    textBox(s, st[2], x + 20, 360, 214, 145, { size: 19, color: C.ink });
    if (i < 3) textBox(s, "→", x + 252, 358, 32, 44, { size: 30, bold: true, color: C.teal, align: "center" });
  });
  note(s, [
    "Phase 2 focuses on persistence, authorization, data lineage, moderation, notifications, and device testing.",
    "Phase 3 establishes scientific evaluation before claiming predictive intelligence. Phase 4 follows only after validation and includes Flutter, operations, accessibility, privacy, and a controlled pilot.",
  ], ["Project repository docs/30_PERCENT_PROGRESS_REPORT.md"]);
}

function addLimits(p) {
  const s = p.slides.add(); header(s, "08 • Academic honesty", "What we are not claiming", 9);
  panel(s, 72, 190, 536, 390, "Current limitations", "• Safety labels are synthetic\n\n• No trained ML model yet\n\n• Citizen report persistence resets\n\n• Runtime risk uses 1 km cells\n\n• Public services are for light testing", C.redPale, C.red);
  panel(s, 672, 190, 536, 390, "Responsible boundaries", "• No claim about real neighbourhood safety\n\n• No private victim/offender data\n\n• Missing data ≠ proven safety\n\n• Not an emergency service\n\n• Never guarantees a route is safe", C.mint, C.green);
  textBox(s, "The prototype demonstrates feasibility—not real-world predictive accuracy.", 72, 615, 1136, 38, { size: 24, bold: true, color: C.navy, align: "center" });
  note(s, [
    "This slide protects the academic credibility of the project. Synthetic hazard values are examples, not statements about actual Mumbai or Navi Mumbai locations.",
    "SOS and continuous location sharing are deferred because they require consent, retention controls, reliability, abuse prevention, and threat modelling.",
  ], ["Project repository README.md", "Project repository docs/30_PERCENT_PROGRESS_REPORT.md"]);
}

function addDemo(p) {
  const s = p.slides.add(); header(s, "09 • Live review", "Five-minute demo sequence", 10);
  const steps = [
    ["01", "Open Citizen demo", "Show map and synthetic label"],
    ["02", "Plan a route", "Choose source, destination and mode"],
    ["03", "Compare profiles", "Fastest vs balanced vs safest"],
    ["04", "Explain the score", "Risk, confidence and reasons"],
    ["05", "Verify a report", "Switch to Government demo"],
  ];
  steps.forEach((st, i) => {
    const y = 188 + i * 86;
    shape(s, "roundRect", 72, y, 1136, 68, i === 2 ? C.mint : C.white, C.border, "rounded-lg");
    shape(s, "roundRect", 88, y + 10, 74, 48, i === 2 ? C.teal : C.navy, "none", "rounded-lg");
    textBox(s, st[0], 101, y + 20, 48, 26, { size: 18, bold: true, color: C.white, align: "center" });
    textBox(s, st[1], 190, y + 18, 350, 32, { size: 22, bold: true, color: C.navy });
    textBox(s, st[2], 560, y + 20, 600, 30, { size: 18, color: C.muted });
  });
  note(s, [
    "Keep the live demo short and deterministic. Route once, compare profiles, inspect one score, submit one report, and show one government action.",
    "If the public routing service is slow, continue with the built APK and verified build/test evidence. Explain that production would use hosted or self-hosted infrastructure.",
  ], ["Project repository docs/PRESENTATION_SCRIPT.md"]);
}

function addClose(p) {
  const s = p.slides.add();
  s.background.fill = C.navy;
  textBox(s, "MILESTONE OUTCOME", 92, 72, 700, 28, { size: 17, bold: true, color: C.amber });
  textBox(s, "The idea is buildable.", 92, 138, 1020, 90, { size: 65, bold: true, color: C.white });
  textBox(s, "Real road routing + verified-hazard lifecycle + explainable safety objective", 92, 252, 980, 80, { size: 29, color: C.mint });
  shape(s, "roundRect", 92, 382, 1096, 134, C.white, "none", "rounded-xl");
  textBox(s, "Next review target", 120, 406, 280, 32, { size: 22, bold: true, color: C.teal });
  textBox(s, "Exact PostGIS road scoring  •  secure persistence  •  baseline ML evaluation protocol", 120, 454, 1020, 36, { size: 21, bold: true, color: C.navy });
  textBox(s, "Questions & feedback", 92, 604, 1096, 38, { size: 25, bold: true, color: C.white, align: "center" });
  note(s, [
    "Close by asking for feedback on three items: whether the ten factors are academically defensible, how the synthetic-data generator should be validated, and what evaluation metrics the department expects for the later ML comparison.",
    "The requested outcome is approval to proceed from feasibility into secure persistence and data/evaluation work.",
  ], ["Project repository docs/30_PERCENT_PROGRESS_REPORT.md"]);
}

async function writeBlob(url, blob) {
  await fs.writeFile(url, new Uint8Array(await blob.arrayBuffer()));
}

async function main() {
  await fs.mkdir(OUT, { recursive: true });
  await fs.mkdir(RENDER, { recursive: true });
  const p = Presentation.create({ slideSize: { width: W, height: H } });
  addCover(p); addProblem(p); addThirty(p); addCurrentDemo(p); addStack(p);
  addRisk(p); addEvidence(p); addRoadmap(p); addLimits(p); addDemo(p); addClose(p);

  for (const [i, slide] of p.slides.items.entries()) {
    const stem = `slide-${String(i + 1).padStart(2, "0")}`;
    await writeBlob(new URL(`${stem}.png`, RENDER), await p.export({ slide, format: "png", scale: 1 }));
    const layout = await slide.export({ format: "layout" });
    await fs.writeFile(new URL(`${stem}.layout.json`, RENDER), await layout.text());
  }
  await writeBlob(new URL("deck-montage.webp", RENDER), await p.export({ format: "webp", montage: true, scale: 1 }));
  const pptx = await PresentationFile.exportPptx(p);
  await pptx.save(fileURLToPath(new URL("Safe-Navi-30-Percent-Progress-Review.pptx", OUT)));
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
