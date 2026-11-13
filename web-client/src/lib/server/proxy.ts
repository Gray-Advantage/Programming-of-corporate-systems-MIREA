import { env } from "$env/dynamic/private";
import { json, type RequestEvent } from "@sveltejs/kit";

export function apiError(event: RequestEvent, status: number, message: string) {
  const incoming = event.request.headers.get("x-correlation-id");
  const correlationId =
    incoming && /^[a-zA-Z0-9-]{1,64}$/.test(incoming)
      ? incoming
      : crypto.randomUUID();
  const code =
    (
      {
        403: "forbidden",
        404: "not_found",
        503: "service_unavailable",
      } as Record<number, string>
    )[status] ?? "request_rejected";
  return json(
    { status, code, message, correlationId },
    {
      status,
      headers: {
        "X-Correlation-ID": correlationId,
        "Cache-Control": "no-store",
      },
    },
  );
}

const refreshing = new Map<string, Promise<any>>();
function setSession(event: RequestEvent, session: any) {
  const options = {
    path: "/",
    httpOnly: true,
    sameSite: "lax" as const,
    secure: event.url.protocol === "https:",
  };
  event.cookies.set("teremok_access", session.accessToken, {
    ...options,
    maxAge: 15 * 60,
  });
  event.cookies.set("teremok_refresh", session.refreshToken, {
    ...options,
    maxAge: 14 * 86400,
  });
}
function clearSession(event: RequestEvent) {
  event.cookies.delete("teremok_access", { path: "/" });
  event.cookies.delete("teremok_refresh", { path: "/" });
}
async function refresh(event: RequestEvent) {
  const token = event.cookies.get("teremok_refresh");
  if (!token) return null;
  let pending = refreshing.get(token);
  if (!pending) {
    pending = fetch(
      `${env.BACKEND_URL || "http://127.0.0.1:8080"}/api/v1/session/refresh`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: token }),
        signal: AbortSignal.timeout(10000),
      },
    ).then(async (r) => (r.ok ? r.json() : null));
    refreshing.set(token, pending);
    // Keep a completed rotation briefly so parallel requests share its result.
    void pending
      .finally(() => setTimeout(() => refreshing.delete(token), 5000))
      .catch(() => {});
  }
  const session = await pending;
  if (session) setSession(event, session);
  else clearSession(event);
  return session?.accessToken ?? null;
}
export async function proxy(event: RequestEvent, backendPath: string) {
  const path = event.params.path ?? "";
  if (!["GET", "HEAD", "OPTIONS"].includes(event.request.method)) {
    const origin = event.request.headers.get("origin");
    if (origin && origin !== event.url.origin)
      return apiError(event, 403, "Источник запроса не разрешён");
  }
  const publicSession = ["session/login", "session/register"].includes(path);
  try {
    let token = event.cookies.get("teremok_access");
    if (!token && !publicSession) token = await refresh(event);
    let body: ArrayBuffer | string | undefined = ["GET", "HEAD"].includes(
      event.request.method,
    )
      ? undefined
      : await event.request.arrayBuffer();
    if (path === "session/logout")
      body = JSON.stringify({
        refreshToken: event.cookies.get("teremok_refresh"),
      });
    const headers = new Headers();
    for (const key of ["content-type", "accept", "range"]) {
      const value = event.request.headers.get(key);
      if (value) headers.set(key, value);
    }
    if (path === "session/logout")
      headers.set("content-type", "application/json");
    headers.set("X-Correlation-ID", crypto.randomUUID());
    const send = () => {
      if (token) headers.set("Authorization", `Bearer ${token}`);
      else headers.delete("Authorization");
      return fetch(
        `${env.BACKEND_URL || "http://127.0.0.1:8080"}${backendPath}${event.url.search}`,
        {
          method: event.request.method,
          headers,
          body,
          signal: AbortSignal.timeout(180000),
        },
      );
    };
    let response = await send();
    if (response.status === 401 && !publicSession) {
      token = await refresh(event);
      if (token) response = await send();
    }
    // Springdoc sees Docker's internal hostname. The browser must call this origin.
    if (
      response.ok &&
      (backendPath === "/v3/api-docs" || backendPath.endsWith("/openapi"))
    ) {
      const schema = await response.json();
      return json(
        {
          ...schema,
          servers: [
            { url: event.url.origin, description: "Текущий сервер Теремка" },
          ],
        },
        { headers: { "Cache-Control": "no-store" } },
      );
    }
    if (publicSession && response.ok) {
      const session = await response.json();
      setSession(event, session);
      return json({ user: session.user });
    }
    if (path === "session/logout") clearSession(event);
    const resultHeaders = new Headers();
    for (const key of [
      "content-type",
      "content-disposition",
      "content-length",
      "content-range",
      "accept-ranges",
      "x-export-failed-tables",
      "x-correlation-id",
    ]) {
      const value = response.headers.get(key);
      if (value) resultHeaders.set(key, value);
    }
    resultHeaders.set("Cache-Control", "no-store");
    return new Response(response.body, {
      status: response.status,
      headers: resultHeaders,
    });
  } catch {
    return apiError(
      event,
      503,
      "Сервер временно недоступен. Повторите попытку",
    );
  }
}
