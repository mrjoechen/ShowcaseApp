type Dependencies = {
  authenticate: (token: string) => Promise<boolean>;
  readValues: (keys: string[]) => Promise<Record<string, string | null>>;
};

const ALLOWED_WEB_ORIGINS = new Set(["https://mrjoechen.github.io"]);
// These are client-visible values, available to authenticated anonymous users too.
// Server-only credentials MUST remain outside this allowlist and behind a proxy.
const USER_VISIBLE_KEYS = new Set(["api_proxy_token", "music_api_auth"]);
const CONFIG_KEY_PATTERN = /^[a-z0-9_]{1,64}$/;
const MAX_REQUEST_BYTES = 1024;

function isAllowedOrigin(origin: string | null): boolean {
  if (origin === null || ALLOWED_WEB_ORIGINS.has(origin)) return true;
  try {
    const url = new URL(origin);
    return (url.hostname === "localhost" || url.hostname === "127.0.0.1") &&
      (url.protocol === "http:" || url.protocol === "https:");
  } catch {
    return false;
  }
}

function responseHeaders(origin: string | null): HeadersInit {
  return {
    "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-region, x-supabase-client-platform",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "86400",
    ...(origin !== null && isAllowedOrigin(origin) ? { "Access-Control-Allow-Origin": origin } : {}),
    "Cache-Control": "private, no-store, max-age=0",
    "Pragma": "no-cache",
    "Content-Security-Policy": "default-src 'none'",
    "Content-Type": "application/json; charset=utf-8",
    "Referrer-Policy": "no-referrer",
    "Vary": "Origin",
    "X-Content-Type-Options": "nosniff",
  };
}

async function readBoundedJson(request: Request): Promise<unknown> {
  const reader = request.body?.getReader();
  if (!reader) throw new Error("Missing body");
  const bytes = new Uint8Array(MAX_REQUEST_BYTES);
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      if (size + value.byteLength > MAX_REQUEST_BYTES) {
        await reader.cancel();
        throw new Error("Request too large");
      }
      bytes.set(value, size);
      size += value.byteLength;
    }
    return JSON.parse(new TextDecoder().decode(bytes.subarray(0, size)));
  } finally {
    reader.releaseLock();
  }
}

export async function handleCriticalConfig(request: Request, dependencies: Dependencies): Promise<Response> {
  const origin = request.headers.get("Origin");
  const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
    status, headers: responseHeaders(origin),
  });
  if (!isAllowedOrigin(origin)) return json(403, { error: "Origin is not allowed" });
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: responseHeaders(origin) });
  }
  if (request.method !== "POST") return json(405, { error: "Method not allowed" });
  const token = /^Bearer\s+(\S+)$/i.exec(request.headers.get("Authorization")?.trim() ?? "")?.[1];
  if (!token) return json(401, { error: "Authentication required" });

  try {
    if (!await dependencies.authenticate(token)) return json(401, { error: "Authentication required" });
    let payload: unknown;
    try {
      payload = await readBoundedJson(request);
    } catch {
      return json(400, { error: "Invalid request body" });
    }
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      return json(400, { error: "Invalid request body" });
    }
    const body = payload as Record<string, unknown>;
    // Keep the previous ShowcaseApp protocol working during client upgrades.
    const legacy = body.keys === undefined && body.config_key !== undefined;
    const rawKeys = legacy ? [body.config_key] : body.keys;
    if (!Array.isArray(rawKeys) || rawKeys.length === 0 || rawKeys.length > 8 ||
      rawKeys.some(key => typeof key !== "string" || !CONFIG_KEY_PATTERN.test(key))) {
      return json(400, { error: "Invalid configuration keys" });
    }
    const keys = [...new Set(rawKeys as string[])];
    if (keys.some(key => !USER_VISIBLE_KEYS.has(key))) {
      return json(403, { error: "Configuration key is not available" });
    }
    const rows = await dependencies.readValues(keys);
    const values = Object.fromEntries(keys
      .filter(key => Object.hasOwn(rows, key))
      .map(key => [key, rows[key]]));
    const missing = keys.filter(key => !Object.hasOwn(values, key));
    if (legacy) {
      return missing.length
        ? json(404, { error: "Configuration not found" })
        : json(200, { config_value: values[keys[0]] });
    }
    return json(200, { values, missing });
  } catch {
    return json(500, { error: "Configuration lookup failed" });
  }
}
