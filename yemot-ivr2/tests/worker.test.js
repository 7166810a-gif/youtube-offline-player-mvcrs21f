import test from "node:test";
import assert from "node:assert/strict";
import { createYemotHandler } from "../worker.js";

const SECRET = "test-secret";
const baseUrl = "https://example.test/yemot/" + SECRET;
const env = { YEMOT_SHARED_SECRET: SECRET, YOUTUBE_API_KEY: "test-api-key" };

function fakeFetch(data, status = 200) {
  return async (url, options) => {
    assert.match(String(url), /^https:\/\/www\.googleapis\.com\/youtube\/v3\/search/);
    assert.equal(options.method, "GET");
    return new Response(JSON.stringify(data), {
      status,
      headers: { "content-type": "application/json" },
    });
  };
}

test("health endpoint returns JSON", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/health"));
  assert.equal(result.status, 200);
  assert.deepEqual(await result.json(), { ok: true, service: "yemot-youtube-search" });
});

test("privacy and terms pages are available", async () => {
  const app = createYemotHandler();
  const privacy = await app.fetch(new Request("https://example.test/privacy"));
  const terms = await app.fetch(new Request("https://example.test/terms"));
  assert.match(await privacy.text(), /מדיניות פרטיות/);
  assert.match(await terms.text(), /תנאי שימוש/);
});

test("secret protects the IVR endpoint", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/yemot/wrong"), env);
  assert.equal(result.status, 404);
});

test("first call requests consent before searching", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request(baseUrl), env);
  assert.match(await result.text(), /read=t-/);
  assert.match(await (async () => result.clone().text())(), /CONSENT,,1,1,7,Digits/);
});

test("consent leads to spoken YouTube search query", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=1"), env);
  assert.equal(await result.text(), "read=t-אמרו את הנושא שאתם רוצים לחפש ב-YouTube=YTQ1,,voice");
});

test("search returns live YouTube video titles and asks for next search", async () => {
  const app = createYemotHandler(fakeFetch({
    items: [
      { id: { videoId: "abc123" }, snippet: { title: "כותרת סרטון לדוגמה" } },
      { id: { videoId: "def456" }, snippet: { title: "סרטון שני" } },
    ],
  }));
  const body = new URLSearchParams({ CONSENT: "1", YTQ1: "חיפוש בעברית" }).toString();
  const result = await app.fetch(new Request(baseUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  const text = await result.text();
  assert.match(text, /תוצאות הבאות/);
  assert.match(text, /כותרת סרטון לדוגמה/);
  assert.match(text, /סרטון שני/);
  assert.match(text, /YTQ2,,voice/);
});

test("YouTube quota errors are translated to a spoken message", async () => {
  const app = createYemotHandler(fakeFetch({
    error: { errors: [{ reason: "quotaExceeded" }] },
  }, 403));
  const body = new URLSearchParams({ CONSENT: "1", YTQ1: "test" }).toString();
  const result = await app.fetch(new Request(baseUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  assert.match(await result.text(), /מכסת החיפוש היומית/);
});

test("does not call YouTube if the user did not consent", async () => {
  let wasCalled = false;
  const app = createYemotHandler(async () => {
    wasCalled = true;
    return new Response("{}", { status: 200 });
  });
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=2"), env);
  assert.match(await result.text(), /ללא הסכמה/);
  assert.equal(wasCalled, false);
});
