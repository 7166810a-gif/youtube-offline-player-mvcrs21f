import test from "node:test";
import assert from "node:assert/strict";
import { createYemotHandler } from "../worker.js";

const SECRET = "test-secret";
const baseUrl = "https://example.test/yemot/" + SECRET;
const env = {
  YEMOT_SHARED_SECRET: SECRET,
  YOUTUBE_API_KEY: "test-api-key",
  YEMOT_API_TOKEN: "yemot-test-token",
  AUTHORIZED_AUDIO_HOSTS: "media.example.com",
  YEMOT_AUDIO_DIR: "YT_AUDIO",
};

function makeCache() {
  const entries = new Map();
  return {
    default: {
      async match(key) {
        const value = entries.get(key.url);
        return value ? value.clone() : undefined;
      },
      async put(key, value) {
        entries.set(key.url, value.clone());
      },
    },
  };
}

function ytFetch(data) {
  return async (url, options) => {
    assert.match(String(url), /^https:\/\/www\.googleapis\.com\/youtube\/v3\/search/);
    assert.equal(options.method, "GET");
    return new Response(JSON.stringify(data), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };
}

test("health endpoint returns JSON", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request("https://example.test/health"));
  assert.equal(result.status, 200);
  assert.deepEqual(await result.json(), { ok: true, service: "yemot-youtube-search-audio" });
});

test("privacy and terms pages are available", async () => {
  const app = createYemotHandler();
  const privacy = await app.fetch(new Request("https://example.test/privacy"));
  const terms = await app.fetch(new Request("https://example.test/terms"));
  assert.match(await privacy.text(), /מדיניות פרטיות/);
  assert.match(await terms.text(), /תנאי שימוש/);
});

test("first call requests consent before any API call", async () => {
  let called = false;
  const app = createYemotHandler(async () => {
    called = true;
    return new Response("{}");
  });
  const result = await app.fetch(new Request(baseUrl), env);
  const text = await result.text();
  assert.match(text, /CONSENT/);
  assert.equal(called, false);
});

test("consent starts a voice search prompt", async () => {
  const app = createYemotHandler();
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=1"), env);
  assert.match(await result.text(), /YTQ1,,voice/);
});

test("search reads live YouTube results then asks caller to choose", async () => {
  const app = createYemotHandler(ytFetch({
    items: [
      { id: { videoId: "abc12345678" }, snippet: { title: "תוצאה אחת" } },
      { id: { videoId: "def12345678" }, snippet: { title: "תוצאה שתיים" } },
    ],
  }), makeCache());
  const body = new URLSearchParams({ CONSENT: "1", YTQ1: "חיפוש בעברית" }).toString();
  const result = await app.fetch(new Request(baseUrl, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  const text = await result.text();
  assert.match(text, /תוצאה אחת/);
  assert.match(text, /תוצאה שתיים/);
  assert.match(text, /PICK1,,1,1,7,Digits/);
});

test("does not extract or play audio from YouTube when no licensed master is mapped", async () => {
  const responseItems = {
    items: [{ id: { videoId: "abc12345678" }, snippet: { title: "סרטון לדוגמה" } }],
  };
  const cache = makeCache();
  const app = createYemotHandler(ytFetch(responseItems), cache, {});
  await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=מבחן"), env);
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=מבחן&PICK1=1"), env);
  assert.match(await result.text(), /אין מקור אודיו נפרד/);
});

test("plays only an authorized independent audio source uploaded to Yemot", async () => {
  const cache = makeCache();
  let uploadCalled = false;
  const app = createYemotHandler(async (url, options) => {
    if (String(url).startsWith("https://www.googleapis.com/youtube/v3/search")) {
      return new Response(JSON.stringify({
        items: [{ id: { videoId: "abc12345678" }, snippet: { title: "שיר מאושר" } }],
      }), { status: 200, headers: { "content-type": "application/json" } });
    }
    if (String(url).startsWith("https://media.example.com/")) {
      return new Response(new Uint8Array([1, 2, 3, 4]), {
        status: 200,
        headers: { "content-type": "audio/mpeg", "content-length": "4" },
      });
    }
    if (String(url).startsWith("https://www.call2all.co.il/ym/api/UploadFile")) {
      uploadCalled = true;
      assert.equal(options.method, "POST");
      assert.equal(options.headers.authorization, "yemot-test-token");
      assert.ok(options.body instanceof FormData);
      return new Response(JSON.stringify({ responseStatus: "OK", path: "ivr2:YT_AUDIO/abc12345678.wav", size: 4 }), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    }
    throw new Error("Unexpected host");
  }, cache, {
    abc12345678: {
      sourceUrl: "https://media.example.com/masters/abc12345678.mp3",
      rightsConfirmed: true,
      rightsNote: "Written owner permission recorded by operator",
    },
  });
  await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=שיר"), env);
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=שיר&PICK1=1"), env);
  const text = await result.text();
  assert.equal(uploadCalled, true);
  assert.match(text, /f-\/YT_AUDIO\/abc12345678/);
  assert.match(text, /YTQ2,,voice/);
});

test("blocks YouTube and Google video URLs as audio sources", async () => {
  const cache = makeCache();
  const app = createYemotHandler(ytFetch({
    items: [{ id: { videoId: "abc12345678" }, snippet: { title: "סרטון" } }],
  }), cache, {
    abc12345678: {
      sourceUrl: "https://www.youtube.com/watch?v=abc12345678",
      rightsConfirmed: true,
      rightsNote: "should still be blocked",
    },
  });
  await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=x"), env);
  const result = await app.fetch(new Request(baseUrl + "?CONSENT=1&YTQ1=x&PICK1=1"), env);
  assert.match(await result.text(), /מקור האודיו אינו ברשימת/);
});
