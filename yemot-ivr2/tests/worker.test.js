import test from "node:test";
import assert from "node:assert/strict";
import { createYemotHandler } from "../worker.js";

const SECRET = "test-secret";
const url = "https://example.test/yemot/" + SECRET;
const app = (catalog = []) => createYemotHandler(catalog);
const env = { YEMOT_SHARED_SECRET: SECRET };

test("health endpoint returns JSON", async () => {
  const result = await app().fetch(new Request("https://example.test/health"), {});
  assert.equal(result.status, 200);
  assert.deepEqual(await result.json(), { ok: true, service: "yemot-ivr2-audio-search" });
});

test("secret protects the API endpoint", async () => {
  const result = await app().fetch(new Request("https://example.test/yemot/wrong"), env);
  assert.equal(result.status, 404);
});

test("first request asks for Hebrew voice search", async () => {
  const result = await app().fetch(new Request(url), env);
  assert.equal(await result.text(), "read=t-שלום, אמרו את שם קובץ השמע שאתם מחפשים=SEARCH1,,voice");
});

test("voice result matches catalog and chains playback with next read", async () => {
  const worker = app([{ title: "שיעור לדוגמה", keywords: ["שיעור", "דוגמה"], file: "/1/001" }]);
  const body = new URLSearchParams({ SEARCH1: "שיעור" }).toString();
  const result = await worker.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  const text = await result.text();
  assert.match(text, /id_list_message=t-מצאתי קובץ מתאים\.t-שיעור לדוגמה\.f-\/1\/001/);
  assert.match(text, /&read=t-/);
  assert.match(text, /=SEARCH2,,voice/);
});

test("invalid file paths are never played", async () => {
  const worker = app([{ title: "פריט לא תקין", keywords: ["חיפוש"], file: "/1/001&go_to_folder=/2" }]);
  const body = new URLSearchParams({ SEARCH1: "חיפוש" }).toString();
  const result = await worker.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
  }), env);
  assert.match(await result.text(), /לא מצאתי קובץ מתאים/);
});

test("supports JSON request bodies", async () => {
  const worker = app([{ title: "חדשות", keywords: ["חדשות"], file: "/1/002" }]);
  const result = await worker.fetch(new Request(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ SEARCH1: "חדשות" }),
  }), env);
  assert.match(await result.text(), /f-\/1\/002/);
});
