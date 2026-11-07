const MAX_SEARCHES_PER_CALL = 10;
const encoder = new TextEncoder();

function plain(body, status = 200, contentType = "text/plain; charset=utf-8") {
  return new Response(body, {
    status,
    headers: { "content-type": contentType, "cache-control": "no-store" },
  });
}

function html(body) {
  return plain(body, 200, "text/html; charset=utf-8");
}

function equalSecret(left, right) {
  const a = encoder.encode(String(left));
  const b = encoder.encode(String(right));
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function readParams(request, url) {
  const params = new URLSearchParams(url.search);
  if (request.method === "GET") return params;
  const type = request.headers.get("content-type") || "";
  try {
    if (type.includes("application/json")) {
      const body = await request.json();
      for (const [key, value] of Object.entries(body || {})) {
        if (value !== undefined && value !== null) params.set(key, String(value));
      }
    } else {
      const raw = await request.text();
      if (raw) {
        const bodyParams = new URLSearchParams(raw);
        for (const [key, value] of bodyParams.entries()) params.set(key, value);
      }
    }
  } catch {
    // Keep query parameters if the IVR request body is empty.
  }
  return params;
}

function latestQuery(params) {
  let latest = null;
  for (const [key, value] of params.entries()) {
    const match = /^YTQ(\d+)$/i.exec(key);
    if (!match) continue;
    const index = Number(match[1]);
    if (!latest || index > latest.index) latest = { index, value };
  }
  return latest;
}

function voiceRead(message, parameter) {
  return "read=t-" + message + "=" + parameter + ",,voice";
}

function digitsRead(message, parameter) {
  return "read=t-" + message + "=" + parameter + ",,1,1,Digits,yes";
}

function safeSpeechText(value) {
  return String(value || "")
    .replace(/[.\\&=\r\n-]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);
}

function resultMessage(items) {
  const ordinals = ["הראשונה", "השנייה", "השלישית", "הרביעית", "החמישית"];
  const parts = ["t-מצאתי תוצאות חיפוש באתר YouTube"];
  items.forEach((item, index) => {
    const title = safeSpeechText(item?.snippet?.title);
    if (title) parts.push("t-תוצאה " + ordinals[index] + " " + title);
  });
  return "id_list_message=" + parts.join(".");
}

async function searchYouTube(query, apiKey, fetchImpl) {
  const url = new URL("https://www.googleapis.com/youtube/v3/search");
  url.searchParams.set("part", "snippet");
  url.searchParams.set("type", "video");
  url.searchParams.set("maxResults", "5");
  url.searchParams.set("order", "relevance");
  url.searchParams.set("regionCode", "IL");
  url.searchParams.set("relevanceLanguage", "he");
  url.searchParams.set("safeSearch", "strict");
  url.searchParams.set("q", query);
  url.searchParams.set("key", apiKey);

  const upstream = await fetchImpl(url.toString(), {
    method: "GET",
    headers: { accept: "application/json" },
  });
  const data = await upstream.json().catch(() => ({}));

  if (!upstream.ok) {
    const reason = data?.error?.errors?.[0]?.reason || "";
    const message = reason === "quotaExceeded"
      ? "מכסת החיפוש היומית של YouTube הסתיימה"
      : reason === "keyInvalid"
        ? "מפתח YouTube API אינו תקין"
        : "שירות החיפוש של YouTube אינו זמין כרגע";
    return { ok: false, message };
  }

  return {
    ok: true,
    items: (Array.isArray(data.items) ? data.items : [])
      .filter((item) => typeof item?.snippet?.title === "string")
      .slice(0, 5),
  };
}

function privacyPage(origin) {
  return `<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>מדיניות פרטיות</title><body style="font-family:Arial,sans-serif;max-width:760px;margin:40px auto;padding:0 20px;line-height:1.7"><h1>מדיניות פרטיות — חיפוש YouTube בטלפון</h1><p>השירות מקבל טקסט של חיפוש קולי ממערכת ימות המשיח, מעביר את מילות החיפוש ל-YouTube Data API ומקריא למתקשר כותרות של סרטונים שחזרו מתוצאות החיפוש.</p><p>השירות אינו שומר מילות חיפוש או תוצאות במסד נתונים. מילות החיפוש נשלחות ל-Google/YouTube לצורך החיפוש, והשימוש כפוף למדיניות הפרטיות של Google ולתנאי YouTube.</p><ul><li><a href="https://policies.google.com/privacy">מדיניות הפרטיות של Google</a></li><li><a href="https://www.youtube.com/t/terms">תנאי השירות של YouTube</a></li></ul><p>כתובת השירות: ${origin}</p></body></html>`;
}

function termsPage(origin) {
  return `<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>תנאי שימוש</title><body style="font-family:Arial,sans-serif;max-width:760px;margin:40px auto;padding:0 20px;line-height:1.7"><h1>תנאי שימוש — חיפוש YouTube בטלפון</h1><p>השירות מאפשר לשלוח שאילתת חיפוש ל-YouTube Data API ולהשמיע בקול את כותרות תוצאות החיפוש.</p><p>השירות אינו שירות של YouTube או Google ואינו מוריד, מחלץ, מפריד או משמיע את האודיו או הווידאו של תוצאות YouTube. לצפייה או האזנה לתוכן יש להשתמש בנגן YouTube הרשמי.</p><p>השימוש כפוף לתנאי השירות של YouTube ולמדיניות הפרטיות של Google.</p><p>כתובת השירות: ${origin}</p></body></html>`;
}

export function createYemotHandler(fetchImpl = fetch) {
  return {
    async fetch(request, env = {}) {
      const url = new URL(request.url);
      if (url.pathname === "/health") {
        return plain(JSON.stringify({ ok: true, service: "yemot-youtube-search" }), 200, "application/json; charset=utf-8");
      }
      if (url.pathname === "/privacy") return html(privacyPage(url.origin));
      if (url.pathname === "/terms") return html(termsPage(url.origin));
      if (request.method !== "GET" && request.method !== "POST") return plain("Method not allowed", 405);

      const secret = String(env.YEMOT_SHARED_SECRET || "");
      if (!secret) return plain("Server is not configured", 503);
      if (!equalSecret(url.pathname, "/yemot/" + secret)) return plain("Not found", 404);

      const params = await readParams(request, url);
      if (params.get("CONSENT") !== "1") {
        if (params.has("CONSENT")) return plain("id_list_message=t-לא ניתן להפעיל את השירות ללא הסכמה");
        return plain(digitsRead(
          "השירות מחפש ב-YouTube ומקריא כותרות בלבד. להמשך הקישו אחת. תנאי שימוש ומדיניות פרטיות זמינים באתר השירות",
          "CONSENT"
        ));
      }

      const query = latestQuery(params);
      if (!query) return plain(voiceRead("אמרו את הנושא שאתם רוצים לחפש באתר YouTube", "YTQ1"));
      if (query.index > MAX_SEARCHES_PER_CALL) {
        return plain("id_list_message=t-הגעתם למספר החיפושים המרבי בשיחה זו");
      }

      const searchTerm = String(query.value || "").trim().slice(0, 300);
      if (!searchTerm) {
        return plain("id_list_message=t-לא זיהיתי מילות חיפוש&" +
          voiceRead("נסו לומר שוב מה לחפש באתר YouTube", "YTQ" + (query.index + 1)));
      }

      const apiKey = String(env.YOUTUBE_API_KEY || "");
      if (!apiKey) return plain("id_list_message=t-מפתח YouTube API עדיין לא הוגדר בשרת");

      try {
        const result = await searchYouTube(searchTerm, apiKey, fetchImpl);
        if (!result.ok) {
          return plain("id_list_message=t-" + result.message + "&" +
            voiceRead("אפשר לנסות חיפוש אחר. אמרו את הנושא", "YTQ" + (query.index + 1)));
        }
        if (result.items.length === 0) {
          return plain("id_list_message=t-לא נמצאו תוצאות ב-YouTube עבור החיפוש&" +
            voiceRead("לחיפוש נוסף, אמרו נושא אחר", "YTQ" + (query.index + 1)));
        }

        return plain(resultMessage(result.items) + "&" +
          voiceRead("לחיפוש נוסף באתר YouTube, אמרו נושא אחר", "YTQ" + (query.index + 1)));
      } catch {
        return plain("id_list_message=t-אירעה תקלה בחיבור ל-YouTube. נסו שוב מאוחר יותר");
      }
    },
  };
}

export default createYemotHandler();
