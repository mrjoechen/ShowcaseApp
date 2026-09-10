import { createClient } from "@supabase/supabase-js";
import { handleCriticalConfig } from "./handler.ts";

const clientOptions = { auth: { autoRefreshToken: false, persistSession: false } };

Deno.serve((request: Request): Promise<Response> => handleCriticalConfig(request, {
  async authenticate(token) {
    const client = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_ANON_KEY") ?? "",
      clientOptions,
    );
    const { data: { user }, error } = await client.auth.getUser(token);
    return !error && user !== null;
  },
  async readValues(keys) {
    // This callback is only reached after user verification and the explicit key allowlist.
    const admin = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
      clientOptions,
    );
    const { data, error } = await admin
      .from("critical_config")
      .select("config_key,config_value")
      .in("config_key", keys);
    if (error) throw new Error("Configuration lookup failed");
    return Object.fromEntries((data ?? []).map(row => [row.config_key, row.config_value]));
  },
}));
