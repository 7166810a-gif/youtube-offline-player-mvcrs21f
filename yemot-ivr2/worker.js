import { CATALOG } from "./catalog.js";

const MAX_SEARCH_ROUNDS = 25;
const encoder = new TextEncoder();

function response(body, status = 200, contentType = "text/plain; charset=utf-8") {
  return new Response(body, {
    status,
    headers: { "content-type": contentType, "cache-control": "no-store" },
  });
}

function constantTimeEqual(left, right) {
  const a = encoder.encode(String(left));
  const b = encoder.encode(String(right));
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i += 1) difference |= a[i] ^ b[i];
  return difference === 0;
}

async function requestParams(request, url) {
  const params = new URLSearchParams(url.search);
  if (request.method === "GET" || request.method === "HEAD") return params;

  const contentType = request.headers.get("content-type") || "";
  try {
    if (contentType.includes("application/json")) {
      const body = await request.json();
      for (const [key, value] of Object.entries(body || {})) {
        if (value !== undefined && value !== null) params.set(key, String(value));
      }
      return params;
    }
    if (contentType.includes("multipart/form-data")) {
      const form = await request.formData();
      for (const [key, value] of form.entries()) {
        params.set(key, typeof value === "string" ? value : value.name);
      }
      return params;
    }
    const raw = await request.text();
    if (raw) {
      const bodyParams = new URLSearchParams(raw);
      for (const [key, value] of bodyParams.entries()) params.set(key, value);
    }
  } catch {
    // Keep URL parameters if the provider sends an empty or unsupported body.
  }
  return params;
}

function latestSearch(params) {
  let latest = null;
  for (const [key, value] of params.entries()) {
    const match = /^SEARCH(\d+)$/i.exec(key);
    if (!match) continue;
    const index = Number(match[1]);
    if (!latest || index > latest.index) latest = { index, value };
  }
  return latest;
}

function normalize(value) {
  return String(value || "")
    .normalize("NFKD")
    .replace(/[\u0591-\u05C7]/g, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function scoreItem(query, item) {
  const searchable = normalize([item.title].concat(item.keywords || []).join(" "));
  const normalizedQuery = normalize(query);
  if (!searchable || !normalizedQuery) return 0;
  if (searchable.includes(normalizedQuery)) return 100;
  const terms = normalizedQuery.split(" ").filter((word) => word.length > 1);
  if (terms.length === 0) return 0;
  const matches = terms.filter((term) => searchable.includes(term)).length;
  return Math.round((matches / terms.length) * 80);
}

function safeSpeechText(value) {
  return String(value || "")
    .replace(/[.\\-&=\r\n]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);
}

function validAudioPath(value) {
  const path = String(value || "");
  return /^\/(?:\d+\/)*\d+$/.test(path) ? path : null;
}

function nextRead(index) {
  if (index > MAX_SEARCH_ROUNDS) return "";
  return "&read=t-לחיפוש קובץ נוסף, אמרו את שמו עכשיו=SEARCH" + index + ",,voice";
}

function makePrompt(index = 1) {
  return "read=t-שלום, אמרו את שם קובץ השמע שאתם מחפשים=SEARCH" + index + ",,voice";
}

function findBestMatch(catalog, query) {
  return catalog
    .filter((item) => item && typeof item.title === "string" && validAudioPath(item.file))
    .map((item) => ({ item, score: scoreItem(query, item) }))
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score)[0]?.item || null;
}

export function createYemotHandler(catalog = CATALOG) {
  return {
    async fetch(request, env = {}) {
      const url = new URL(request.url);

      if (url.pathname === "/health") {
        return response(JSON.stringify({ ok: true, service: "yemot-ivr2-audio-search" }), 200, "application/json; charset=utf-8");
      }
      if (request.method !== "GET" && request.method !== "POST") {
        return response("Method not allowed", 405);
      }

      const secret = String(env.YEMOT_SHARED_SECRET || "");
      if (!secret) return response("Server is not configured", 503);
      const expectedPath = "/yemot/" + secret;
      if (!constantTimeEqual(url.pathname, expectedPath)) return response("Not found", 404);

      const params = await requestParams(request, url);
      const search = latestSearch(params);
      if (!search) return response(makePrompt(1));

      const query = String(search.value || "")
        .replace(/^Digits-/i, "")
        .trim()
        .slice(0, 180);

      const match = query ? findBestMatch(catalog, query) : null;
      const prompt = nextRead(search.index + 1);

      if (!match) {
        const message = query ? "לא מצאתי קובץ מתאים" : "לא זיהיתי את החיפוש";
        return response("id_list_message=t-" + message + prompt);
      }

      const title = safeSpeechText(match.title) || "קובץ שמע";
      const file = validAudioPath(match.file);
      return response("id_list_message=t-מצאתי קובץ מתאים.t-" + title + ".f-" + file + prompt);
    },
  };
}

export default createYemotHandler();
