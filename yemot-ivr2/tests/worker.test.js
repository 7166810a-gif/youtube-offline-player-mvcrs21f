import test from "node:test";
import assert from "node:assert/strict";
import { createYemotHandler } from "../worker.js";

const SECRET = "test-secret";
const url = "https://example.test/yemot/" + SECRET;
const env = { YEMOT_SHARED_SECRET: SECRET, YOUTUBE_API_KEY: "fake-youtube-key" };

test("health endpoint returns JSON", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/health"), {});
  assert.equal(result.status, 200);
  assert.deepEqual(await result.json(), { ok: true, service: "yemot-ivr2-youtube-search" });
});

test("secret protects the API endpoint", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/yemot/wrong"), env);
  assert.equal(result.status, 404);
});

test("first request asks for a Hebrew YouTube voice query", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request(url), env);
  assert.equal(await result.text(), "read=t-שלום, אמרו מה תרצו לחפש ביוטיוב=SEARCH,,voice");
});

test("voice query calls YouTube search and reads back titles", async () => {
  let calledUrl;
  const fakeFetch = async (input) => {
    calledUrl = new URL(input);
    return new Response(JSON.stringify({
      items: [
        { id: { videoId: "abc123" }, snippet: { title: "כותרת לדוגמה", channelTitle: "ערוץ לדוגמה" } },
        { id: { videoId: "def456" }, snippet: { title: "סרטון נוסף", channelTitle: "ערוץ שני" } },
      ],
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const app = createYemotHandler(fakeFetch);
  const body = new URLSearchParams({ SEARCH: "מוזיקה לדוגמה" }).toString();
  const result = await app.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);

  assert.equal(calledUrl.searchParams.get("q"), "מוזיקה לדוגמה");
  assert.equal(calledUrl.searchParams.get("type"), "video");
  assert.equal(calledUrl.searchParams.get("key"), "fake-youtube-key");
  const text = await result.text();
  assert.match(text, /כותרת לדוגמה/);
  assert.match(text, /ערוץ לדוגמה/);
  assert.match(text, /סרטון נוסף/);
});

test("reports missing YouTube API key without exposing secrets", async () => {
  const app = createYemotHandler();
  const body = new URLSearchParams({ SEARCH: "שאילתת בדיקה" }).toString();
  const result = await app.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), { YEMOT_SHARED_SECRET: SECRET });
  assert.match(await result.text(), /מפתח החיפוש של יוטיוב/);
});

test("handles YouTube API failures with a caller-friendly message", async () => {
  const app = createYemotHandler(async () => new Response("quota exceeded", { status: 403 }));
  const body = new URLSearchParams({ SEARCH: "חיפוש" }).toString();
  const result = await app.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  assert.match(await result.text(), /אירעה תקלה בחיפוש ביוטיוב/);
});
