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

function getSearchQuery(params) {
  // The API read action sends SEARCH=<recognized speech>.
  // SEARCH1 is also accepted for compatibility with earlier test/config versions.
  return params.get("SEARCH") ?? params.get("SEARCH1") ?? "";
}

function safeSpeechText(value) {
  return String(value || "")
    .replace(/<[^>]*>/g, " ")
    .replace(/[.\\-&=\r\n]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 125);
}

function makePrompt() {
  return "read=t-שלום, אמרו מה תרצו לחפש ביוטיוב=SEARCH,,voice";
}

async function searchYouTube(query, apiKey, fetchImpl) {
  const url = new URL("https://www.googleapis.com/youtube/v3/search");
  url.searchParams.set("part", "snippet");
  url.searchParams.set("type", "video");
  url.searchParams.set("q", query);
  url.searchParams.set("maxResults", "5");
  url.searchParams.set("relevanceLanguage", "iw");
  url.searchParams.set("regionCode", "IL");
  url.searchParams.set("safeSearch", "moderate");
  url.searchParams.set("key", apiKey);

  const result = await fetchImpl(url.toString(), {
    method: "GET",
    headers: { accept: "application/json" },
  });

  if (!result.ok) {
    const detail = await result.text().catch(() => "");
    throw new Error("YouTube API returned " + result.status + ": " + detail.slice(0, 300));
  }

  const data = await result.json();
  return Array.isArray(data.items) ? data.items : [];
}

function makeResultsMessage(items) {
  if (!items.length) return "id_list_message=t-לא נמצאו סרטונים מתאימים ביוטיוב";

  const segments = ["t-מצאתי " + items.length + " תוצאות ביוטיוב"];
  items.forEach((item, index) => {
    const title = safeSpeechText(item.snippet?.title) || "ללא כותרת";
    const channel = safeSpeechText(item.snippet?.channelTitle);
    segments.push("t-תוצאה " + (index + 1) + " " + title);
    if (channel) segments.push("t-בערוץ " + channel);
  });
  segments.push("t-לשמיעת הסרטונים יש לפתוח את הקישור ביוטיוב במכשיר מתאים");
  return "id_list_message=" + segments.join(".");
}

export function createYemotHandler(fetchImpl = fetch) {
  return {
    async fetch(request, env = {}) {
      const url = new URL(request.url);

      if (url.pathname === "/health") {
        return response(JSON.stringify({ ok: true, service: "yemot-ivr2-youtube-search" }), 200, "application/json; charset=utf-8");
      }
      if (request.method !== "GET" && request.method !== "POST") {
        return response("Method not allowed", 405);
      }

      const secret = String(env.YEMOT_SHARED_SECRET || "");
      if (!secret) return response("Server is not configured", 503);
      if (!constantTimeEqual(url.pathname, "/yemot/" + secret)) return response("Not found", 404);

      const params = await requestParams(request, url);
      const query = getSearchQuery(params).replace(/^Digits-/i, "").trim().slice(0, 180);

      if (!query) return response(makePrompt());

      const apiKey = String(env.YOUTUBE_API_KEY || "");
      if (!apiKey) {
        return response("id_list_message=t-מפתח החיפוש של יוטיוב עדיין לא הוגדר בשרת");
      }

      try {
        const items = await searchYouTube(query, apiKey, fetchImpl);
        return response(makeResultsMessage(items));
      } catch {
        return response("id_list_message=t-אירעה תקלה בחיפוש ביוטיוב. נסו שוב מאוחר יותר");
      }
    },
  };
}

export default createYemotHandler();
