import type { RequestHandler } from "./$types";
import { proxy, apiError } from "$lib/server/proxy";
const handle: RequestHandler = (event) => {
  const path = event.params.path;
  if (
    path === "session/refresh" ||
    path.startsWith("library/works/import/") ||
    !/^[a-zA-Z0-9_./-]+$/.test(path) ||
    path.includes("..")
  )
    return apiError(event, 404, "Адрес не найден");
  return proxy(event, `/api/v1/${path}`);
};
export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const DELETE = handle;
export const PATCH = handle;
