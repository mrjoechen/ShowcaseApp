import assert from "node:assert/strict";
import test from "node:test";
import { handleCriticalConfig } from "./handler.ts";

function request(body, { token = "test-jwt", origin, method = "POST" } = {}) {
  return new Request("https://example.supabase.co/functions/v1/get-critical-config", {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(origin ? { Origin: origin } : {}),
      "Content-Type": "application/json",
    },
    ...(method === "POST" ? { body: typeof body === "string" ? body : JSON.stringify(body) } : {}),
  });
}

const noRead = () => { assert.fail("Rejected requests must not query configuration"); };
const authenticated = { authenticate: async () => true, readValues: noRead };

test("missing bearer token is rejected before authentication or data lookup", async () => {
  const response = await handleCriticalConfig(request({ keys: ["music_api_auth"] }, { token: null }), {
    authenticate: noRead, readValues: noRead,
  });
  assert.equal(response.status, 401);
});

test("invalid or expired bearer cannot read configurations", async () => {
  const response = await handleCriticalConfig(request({ keys: ["music_api_auth"] }), {
    authenticate: async (token) => { assert.equal(token, "test-jwt"); return false; }, readValues: noRead,
  });
  assert.equal(response.status, 401);
});

test("batch protocol returns only requested keys and explicit missing keys", async () => {
  const response = await handleCriticalConfig(request({ keys: ["music_api_auth", "api_proxy_token", "music_api_auth"] }), {
    authenticate: async () => true,
    readValues: async (keys) => {
      assert.deepEqual(keys, ["music_api_auth", "api_proxy_token"]);
      return { music_api_auth: "example-client-value", server_only_secret: "must-not-return" };
    },
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), {
    values: { music_api_auth: "example-client-value" }, missing: ["api_proxy_token"],
  });
  assert.match(response.headers.get("Cache-Control"), /no-store/);
});

test("unknown keys are denied before database lookup including legacy requests", async () => {
  for (const body of [{ keys: ["server_only_secret"] }, { config_key: "server_only_secret" }]) {
    assert.equal((await handleCriticalConfig(request(body), authenticated)).status, 403);
  }
});

test("malformed and excessive keys cannot reach the database", async () => {
  for (const body of [null, [], {}, { keys: [] }, { keys: "music_api_auth" },
    { keys: Array(9).fill("music_api_auth") }, { keys: ["../secret"] },
    { keys: ["a".repeat(65)] }, { keys: [null] }, { keys: ["Uppercase"] }, "{"]) {
    assert.equal((await handleCriticalConfig(request(body), authenticated)).status, 400);
  }
});

test("body size is bounded before JSON parsing", async () => {
  assert.equal((await handleCriticalConfig(request(" ".repeat(1025)), authenticated)).status, 400);
});

test("an empty table returns the reference missing-key response", async () => {
  const response = await handleCriticalConfig(request({ keys: ["music_api_auth"] }), {
    ...authenticated, readValues: async () => ({}),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { values: {}, missing: ["music_api_auth"] });
});

test("legacy single-key callers remain compatible", async () => {
  const response = await handleCriticalConfig(request({ config_key: "music_api_auth" }), {
    ...authenticated, readValues: async () => ({ music_api_auth: "example-client-value" }),
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { config_value: "example-client-value" });
  const missing = await handleCriticalConfig(request({ config_key: "music_api_auth" }), {
    ...authenticated, readValues: async () => ({}),
  });
  assert.equal(missing.status, 404);
});

test("unexpected upstream errors do not expose their details", async () => {
  const response = await handleCriticalConfig(request({ keys: ["music_api_auth"] }), {
    ...authenticated, readValues: async () => { throw new Error("database-secret-value"); },
  });
  assert.equal(response.status, 500);
  assert.doesNotMatch(await response.text(), /database-secret-value/);
});

test("only trusted browser origins receive CORS permission", async () => {
  for (const origin of ["https://mrjoechen.github.io", "http://localhost:8080"]) {
    const response = await handleCriticalConfig(request({}, { origin, method: "OPTIONS" }), authenticated);
    assert.equal(response.status, 204);
    assert.equal(response.headers.get("Access-Control-Allow-Origin"), origin);
  }
  const response = await handleCriticalConfig(request({}, { origin: "https://evil.example" }), authenticated);
  assert.equal(response.status, 403);
  assert.equal(response.headers.get("Access-Control-Allow-Origin"), null);
});

test("GET never queries configuration", async () => {
  assert.equal((await handleCriticalConfig(request(null, { method: "GET" }), authenticated)).status, 405);
});

test("trusted browser preflight permits the headers sent by the pinned Kotlin SDK", async () => {
  const response = await handleCriticalConfig(new Request("https://example.test", {
    method: "OPTIONS",
    headers: {
      Origin: "https://mrjoechen.github.io",
      "Access-Control-Request-Method": "POST",
      "Access-Control-Request-Headers": "authorization,apikey,content-type,x-region,x-supabase-client-platform",
    },
  }), authenticated);
  assert.equal(response.status, 204);
  const allowed = response.headers.get("Access-Control-Allow-Headers").split(",").map(value => value.trim().toLowerCase());
  for (const header of ["authorization", "apikey", "content-type", "x-region", "x-supabase-client-platform"]) {
    assert.ok(allowed.includes(header), `Browser must be able to send ${header}`);
  }
});
