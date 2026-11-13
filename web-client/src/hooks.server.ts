import type { Handle } from "@sveltejs/kit";
import { proxy, apiError } from "$lib/server/proxy";
export const handle: Handle = async ({ event, resolve }) => {
  // CSV import is a console workflow. Keep its REST API on the backend only.
  if (event.url.pathname.startsWith("/api/v1/library/works/import/"))
    return apiError(event, 404, "Адрес не найден");
  // Keep the Swagger document URL in its asset directory after Springdoc's redirect.
  if (event.url.pathname === "/swagger-ui.html")
    return new Response(null, {
      status: 302,
      headers: { Location: "/swagger-ui/index.html" },
    });
  if (
    event.url.pathname.startsWith("/swagger-ui") ||
    event.url.pathname.startsWith("/v3/api-docs") ||
    event.url.pathname.startsWith("/api/v1/")
  )
    return proxy(event, event.url.pathname);
  const response = await resolve(event);
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Referrer-Policy", "same-origin");
  response.headers.set("X-Frame-Options", "SAMEORIGIN");
  return response;
};
