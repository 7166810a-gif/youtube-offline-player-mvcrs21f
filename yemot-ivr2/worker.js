import { LICENSED_AUDIO } from "./licensed-audio.js";

const MAX_SEARCHES_PER_CALL = 10;
const MAX_AUDIO_BYTES = 45 * 1024 * 1024;
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
    } else if (type.includes("multipart/form-data")) {
      const form = await request.formData();
      for (const [key, value] of form.entries()) {
        params.set(key, typeof value === "string" ? value : value.name);
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

function latestParam(params, prefix) {
  let latest = null;
  for (const [key, value] of params.entries()) {
    const match = new RegExp("^" + prefix + "(\\d+)$", "i").exec(key);
    if (!match) continue;
    const index = Number(match[1]);
    if (!latest || index > latest.index) latest = { index, value };
  }
  return latest;
}

function voiceRead(message, parameter) {
  return "read=t-" + message + "=" + parameter + ",,voice";
}

function digitsRead(message, parameter, maxDigits = 1) {
  return "read=t-" + message + "=" + parameter + ",," + maxDigits + ",1,7,Digits";
}

function spokenText(value) {
  // These characters are separators in the Yemot response syntax.
  return String(value || "")
    .replace(/[.\\&=\r\n-]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);
}

function buildResultMessage(items) {
  const ordinals = ["הראשונה", "השנייה", "השלישית", "הרביעית", "החמישית"];
  const parts = ["t-מצאתי תוצאות חיפוש באתר YouTube"];
  items.forEach((item, index) => {
    const title = spokenText(item?.snippet?.title);
    if (title) parts.push("t-לתוצאה " + ordinals[index] + ": " + title);
  });
  return "id_list_message=" + parts.join(".");
}

function normalizeVideoId(value) {
  const id = String(value || "");
  return /^[A-Za-z0-9_-]{6,80}$/.test(id) ? id : null;
}

function sourceIsAllowed(sourceUrl, allowedHosts) {
  let parsed;
  try {
    parsed = new URL(sourceUrl);
  } catch {
    return false;
  }

  if (parsed.protocol !== "https:" || parsed.username || parsed.password) return false;

  const host = parsed.hostname.toLowerCase();
  const blocked = [
    "youtube.com", "www.youtube.com", "youtu.be", "m.youtube.com",
    "googlevideo.com", "youtube-nocookie.com", "ytimg.com",
  ];
  if (blocked.some((domain) => host === domain || host.endsWith("." + domain))) {
    return false;
  }

  const allowlist = String(allowedHosts || "")
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
  return allowlist.includes(host);
}

function cacheKeyFor(query) {
  const url = new URL("https://yt-search-cache.invalid/v1");
  url.searchParams.set("q", query.trim().toLocaleLowerCase());
  return new Request(url.toString(), { method: "GET" });
}

async function searchYouTube(query, apiKey, fetchImpl, cacheApi) {
  const cache = cacheApi?.default;
  const cacheKey = cacheKeyFor(query);

  if (cache) {
    try {
      const cached = await cache.match(cacheKey);
      if (cached) return { ok: true, items: await cached.json(), cached: true };
    } catch {
      // Cache is an optimization only; continue to the API.
    }
  }

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
    return { ok: false, message, status: upstream.status };
  }

  const items = Array.isArray(data.items)
    ? data.items.filter((item) =>
        typeof item?.snippet?.title === "string" &&
        typeof item?.id?.videoId === "string" &&
        normalizeVideoId(item.id.videoId)
      ).slice(0, 5)
    : [];

  if (cache) {
    try {
      await cache.put(cacheKey, new Response(JSON.stringify(items), {
        headers: { "cache-control": "public, max-age=300", "content-type": "application/json" },
      }));
    } catch {
      // Do not fail a search because the cache is unavailable.
    }
  }

  return { ok: true, items, cached: false };
}

async function uploadAuthorizedAudio({ asset, videoId, env, fetchImpl }) {
  if (asset?.rightsConfirmed !== true || !asset?.rightsNote || !asset?.sourceUrl) {
    return { ok: false, message: "לא הוגדר מקור אודיו נפרד עם אישור שימוש טלפוני" };
  }

  if (!sourceIsAllowed(asset.sourceUrl, env.AUTHORIZED_AUDIO_HOSTS)) {
    return { ok: false, message: "מקור האודיו אינו ברשימת השרתים המאושרים" };
  }

  const apiToken = String(env.YEMOT_API_TOKEN || "");
  if (!apiToken) return { ok: false, message: "לא הוגדר מפתח API להעלאת קבצים לימות" };

  let sourceResponse;
  try {
    sourceResponse = await fetchImpl(asset.sourceUrl, {
      method: "GET",
      headers: { accept: "audio/*, application/octet-stream" },
    });
  } catch {
    return { ok: false, message: "לא הצלחתי לגשת לקובץ האודיו שסופק" };
  }

  if (!sourceResponse.ok) {
    return { ok: false, message: "מקור האודיו לא החזיר קובץ תקין" };
  }

  const contentType = sourceResponse.headers.get("content-type") || "";
  if (!(contentType.toLowerCase().startsWith("audio/") ||
        contentType.toLowerCase().includes("application/octet-stream"))) {
    return { ok: false, message: "הקישור שסופק אינו קובץ אודיו" };
  }

  const declaredLength = Number(sourceResponse.headers.get("content-length") || 0);
  if (declaredLength > MAX_AUDIO_BYTES) {
    return { ok: false, message: "קובץ האודיו גדול מדי להעלאה אחת" };
  }

  let bytes;
  try {
    bytes = await sourceResponse.arrayBuffer();
  } catch {
    return { ok: false, message: "הורדת קובץ האודיו נכשלה" };
  }
  if (bytes.byteLength === 0) return { ok: false, message: "קובץ האודיו ריק" };
  if (bytes.byteLength > MAX_AUDIO_BYTES) {
    return { ok: false, message: "קובץ האודיו גדול מדי להעלאה אחת" };
  }

  const directory = String(env.YEMOT_AUDIO_DIR || "YT_AUDIO").replace(/^\/+|\/+$/g, "");
  if (!/^[A-Za-z0-9_/-]{1,80}$/.test(directory)) {
    return { ok: false, message: "תיקיית האודיו בימות אינה תקינה" };
  }

  const targetPath = "ivr2:" + directory + "/" + videoId + ".wav";
  const uploadUrl = new URL("https://www.call2all.co.il/ym/api/UploadFile");
  uploadUrl.searchParams.set("path", targetPath);
  uploadUrl.searchParams.set("convertAudio", "1");
  uploadUrl.searchParams.set("autoNumbering", "false");

  const form = new FormData();
  const sourceExtension = /\.ogg(?:$|\?)/i.test(asset.sourceUrl) ? "ogg" :
    /\.wav(?:$|\?)/i.test(asset.sourceUrl) ? "wav" : "mp3";
  form.append("qqfile", new Blob([bytes], { type: contentType }), videoId + "." + sourceExtension);
  form.append("uploader", "yemot-admin");

  let uploadResponse;
  try {
    uploadResponse = await fetchImpl(uploadUrl.toString(), {
      method: "POST",
      headers: { authorization: apiToken },
      body: form,
    });
  } catch {
    return { ok: false, message: "החיבור לשרת העלאת הקבצים של ימות נכשל" };
  }

  const uploadData = await uploadResponse.json().catch(() => ({}));
  if (!uploadResponse.ok || (uploadData.responseStatus && uploadData.responseStatus !== "OK")) {
    return { ok: false, message: "ימות לא אישרה את העלאת קובץ האודיו" };
  }

  return { ok: true, filePath: "/" + directory + "/" + videoId };
}

function privacyPage(origin) {
  return `<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>מדיניות פרטיות</title><body style="font-family:Arial,sans-serif;max-width:760px;margin:40px auto;padding:0 20px;line-height:1.7"><h1>מדיניות פרטיות — חיפוש והשמעת אודיו</h1><p>השירות מקבל מילות חיפוש ממערכת ימות המשיח, שולח אותן ל-YouTube Data API ומקריא כותרות של תוצאות חיפוש. השמעה בטלפון נעשית רק מקובץ אודיו חיצוני שסופק בנפרד על ידי בעל הזכויות, עם אישור מפורש להפצה ולהשמעה טלפונית.</p><p>השרת אינו מוריד או מחלץ אודיו או וידאו מתוך YouTube. מילות החיפוש נשלחות ל-Google/YouTube. קובץ האודיו המורשה מועבר לשרת ימות ונשמר שם לצורך ההשמעה. השירות אינו שומר את מילות החיפוש או ההקלטות במסד נתונים משלו.</p><p>השימוש כפוף ל<a href="https://policies.google.com/privacy">מדיניות הפרטיות של Google</a> ול<a href="https://www.youtube.com/t/terms">תנאי YouTube</a>.</p><p>כתובת השירות: ${origin}</p></body></html>`;
}

function termsPage(origin) {
  return `<!doctype html><html lang="he" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>תנאי שימוש</title><body style="font-family:Arial,sans-serif;max-width:760px;margin:40px auto;padding:0 20px;line-height:1.7"><h1>תנאי שימוש — חיפוש והשמעת אודיו</h1><p>החיפוש מתבצע ב-YouTube Data API. כותרות סרטונים מוקראות למתקשר. קובץ אודיו יושמע בטלפון רק כאשר בעל הזכויות מספק קובץ אודיו עצמאי ומאשר במפורש את השימוש, ההמרה וההשמעה הטלפונית שלו.</p><p>השירות אינו מוריד, מחלץ, מפריד או משמיע אודיו מתוך קובצי YouTube. הוא אינו שירות של YouTube או Google.</p><p>בעל השירות אחראי לוודא שיש בידו הרשאות מתאימות לכל קובץ אודיו שנכלל במאגר.</p><p>כתובת השירות: ${origin}</p></body></html>`;
}

export function createYemotHandler(fetchImpl = fetch, cacheApi = globalThis.caches, audioRegistry = LICENSED_AUDIO) {
  return {
    async fetch(request, env = {}) {
      const url = new URL(request.url);

      if (url.pathname === "/health") {
        return plain(JSON.stringify({ ok: true, service: "yemot-youtube-search-audio" }), 200, "application/json; charset=utf-8");
      }
      if (url.pathname === "/privacy") return html(privacyPage(url.origin));
      if (url.pathname === "/terms") return html(termsPage(url.origin));

      if (request.method !== "GET" && request.method !== "POST") {
        return plain("Method not allowed", 405);
      }

      const secret = String(env.YEMOT_SHARED_SECRET || "");
      if (!secret) return plain("Server is not configured", 503);
      if (!equalSecret(url.pathname, "/yemot/" + secret)) return plain("Not found", 404);

      const params = await readParams(request, url);

      if (params.get("CONSENT") !== "1") {
        if (params.has("CONSENT")) {
          return plain("id_list_message=t-לא ניתן להפעיל את השירות ללא הסכמה");
        }
        return plain(digitsRead(
          "השירות מחפש ב-YouTube ומשמיע קובץ אודיו נפרד רק באישור בעל הזכויות. להמשך הקישו אחת. מדיניות פרטיות זמינה באתר השירות",
          "CONSENT",
          1
        ));
      }

      const query = latestParam(params, "YTQ");
      if (!query) {
        return plain(voiceRead("אמרו את הנושא שאתם רוצים לחפש באתר YouTube", "YTQ1"));
      }
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
        const result = await searchYouTube(searchTerm, apiKey, fetchImpl, cacheApi);
        if (!result.ok) {
          return plain("id_list_message=t-" + result.message + "&" +
            voiceRead("אפשר לנסות חיפוש אחר. אמרו את הנושא", "YTQ" + (query.index + 1)));
        }
        if (result.items.length === 0) {
          return plain("id_list_message=t-לא נמצאו תוצאות ב-YouTube עבור החיפוש&" +
            voiceRead("לחיפוש נוסף, אמרו נושא אחר", "YTQ" + (query.index + 1)));
        }

        const pickParam = latestParam(params, "PICK");
        if (!pickParam || pickParam.index !== query.index) {
          return plain(buildResultMessage(result.items) + "&" +
            digitsRead("לבחירת תוצאה, הקישו מספר מאחת עד חמש", "PICK" + query.index, 1));
        }

        const chosenNumber = Number(pickParam.value);
        if (!Number.isInteger(chosenNumber) || chosenNumber < 1 || chosenNumber > result.items.length) {
          return plain("id_list_message=t-הבחירה אינה תקינה&" +
            voiceRead("לחיפוש נוסף, אמרו נושא אחר", "YTQ" + (query.index + 1)));
        }

        const chosen = result.items[chosenNumber - 1];
        const videoId = normalizeVideoId(chosen?.id?.videoId);
        const asset = videoId ? audioRegistry[videoId] : null;

        if (!asset) {
          return plain("id_list_message=t-לתוצאה שבחרתם אין מקור אודיו נפרד שאושר להשמעה בטלפון. אין אפשרות להשמיע את פס הקול מתוך YouTube&" +
            voiceRead("לחיפוש תוכן אחר, אמרו נושא חדש", "YTQ" + (query.index + 1)));
        }

        const uploaded = await uploadAuthorizedAudio({
          asset,
          videoId,
          env,
          fetchImpl,
        });
        if (!uploaded.ok) {
          return plain("id_list_message=t-" + uploaded.message + "&" +
            voiceRead("לחיפוש נושא אחר, אמרו את הנושא", "YTQ" + (query.index + 1)));
        }

        const title = spokenText(chosen?.snippet?.title) || "הקלטה";
        const nextSearch = voiceRead(
          "להמשך חיפוש באתר YouTube, אמרו נושא אחר",
          "YTQ" + (query.index + 1)
        );
        return plain(
          "id_list_message=t-נבחרה התוצאה " + chosenNumber + ".t-" + title +
          ".t-כעת נשמיע את קובץ האודיו שסופק באישור בעל הזכויות.f-" + uploaded.filePath +
          "&" + nextSearch
        );
      } catch {
        return plain("id_list_message=t-אירעה תקלה בתהליך החיפוש או ההשמעה. נסו שוב מאוחר יותר");
      }
    },
  };
}

export default createYemotHandler();
