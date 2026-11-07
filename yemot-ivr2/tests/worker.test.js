import test from "node:test";
import assert from "node:assert/strict";
import { createYemotHandler } from "../worker.js";

const SECRET = "test-secret";
const url = "https://example.test/yemot/" + SECRET;
const env = { YEMOT_SHARED_SECRET: SECRET, YOUTUBE_API_KEY: "test-api-key" };

function fakeFetch(data, status = 200) {
  return async (target, options) => {
    assert.match(String(target), /^https:\/\/www\.googleapis\.com\/youtube\/v3\/search/);
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

test("shared secret protects the IVR endpoint", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/yemot/wrong"), env);
  assert.equal(result.status, 404);
});

test("first request asks for consent", async () => {
  let called = false;
  const app = createYemotHandler(async () => {
    called = true;
    return new Response("{}");
  });
  const result = await app.fetch(new Request(url), env);
  assert.match(await result.text(), /CONSENT/);
  assert.equal(called, false);
});

test("consent starts voice search prompt", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request(url + "?CONSENT=1"), env);
  assert.match(await result.text(), /YTQ1,,voice/);
});

test("search returns YouTube titles and asks for another query", async () => {
  const app = createYemotHandler(fakeFetch({
    items: [
      { id: { videoId: "abc123" }, snippet: { title: "כותרת סרטון לדוגמה" } },
      { id: { videoId: "def456" }, snippet: { title: "סרטון שני" } },
    ],
  }));
  const body = new URLSearchParams({ CONSENT: "1", YTQ1: "חיפוש בעברית" }).toString();
  const result = await app.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  const text = await result.text();
  assert.match(text, /כותרת סרטון לדוגמה/);
  assert.match(text, /סרטון שני/);
  assert.match(text, /YTQ2,,voice/);
  assert.doesNotMatch(text, /f-/);
});

test("quota errors become a spoken message", async () => {
  const app = createYemotHandler(fakeFetch({
    error: { errors: [{ reason: "quotaExceeded" }] },
  }, 403));
  const body = new URLSearchParams({ CONSENT: "1", YTQ1: "test" }).toString();
  const result = await app.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  assert.match(await result.text(), /מכסת החיפוש היומית/);
});
