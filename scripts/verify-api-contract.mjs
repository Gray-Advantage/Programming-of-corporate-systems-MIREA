import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";

const base = process.env.BACKEND_URL || "http://localhost:8080";
const web = process.env.WEB_URL || "http://localhost:5173";
const env = await readFile(new URL("../.env", import.meta.url), "utf8");
const setting = (key) =>
  env.match(new RegExp(`^${key}=(.*)$`, "m"))?.[1].trim();
const checks = [];
const correlation = `contract-${randomUUID()}`;
async function call(
  path,
  {
    method = "GET",
    body,
    token,
    status = 200,
    raw = false,
    origin = base,
    headers = {},
  } = {},
) {
  const response = await fetch(origin + path, {
    method,
    headers: {
      "X-Correlation-ID": correlation,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...headers,
    },
    body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(30000),
  });
  const text = await response.text();
  assert.equal(
    response.status,
    status,
    `${method} ${path}: HTTP ${response.status} ${text.slice(0, 300)}`,
  );
  const result = text ? JSON.parse(text) : null;
  if (status >= 400) {
    assert.match(
      response.headers.get("content-type") ?? "",
      /application\/json/,
    );
    assert.deepEqual(Object.keys(result).sort(), [
      "code",
      "correlationId",
      "message",
      "status",
    ]);
    assert.equal(result.status, status);
    assert.ok(result.code && result.message && result.correlationId);
    assert.equal(
      response.headers.get("x-correlation-id"),
      result.correlationId,
    );
    assert.ok(
      !/stackTrace|SQLException|org\.springframework|SELECT .* FROM/i.test(
        text,
      ),
    );
    checks.push(`${method} ${path} → ${status}: common error contract`);
  }
  return result;
}
const api = (path, options) => call("/api/v1/" + path, options);
const admin = await api("session/login", {
  method: "POST",
  body: {
    login: setting("BOOTSTRAP_ADMIN_LOGIN") || "admin",
    password: setting("BOOTSTRAP_ADMIN_PASSWORD"),
  },
});
let editor, reader, work;
const run = randomUUID().slice(0, 8),
  missing = randomUUID();
try {
  editor = await api("session/register", {
    method: "POST",
    status: 201,
    body: {
      login: `contract-editor-${run}`,
      name: "Проверка контракта",
      password: `Aa1-${randomUUID()}`,
    },
  });
  reader = await api("session/register", {
    method: "POST",
    status: 201,
    body: {
      login: `contract-reader-${run}`,
      name: "Проверка чтения",
      password: `Aa1-${randomUUID()}`,
    },
  });
  await api(`admin/users/${reader.user.id}`, {
    method: "PUT",
    token: admin.accessToken,
    body: { ...reader.user, roles: ["READER"], password: "" },
  });
  const token = editor.accessToken;

  await api("session/login", { method: "POST", body: {}, status: 400 });
  await api("session/login", {
    method: "POST",
    raw: true,
    body: "{",
    status: 400,
  });
  await api("session/refresh", {
    method: "POST",
    body: { refreshToken: "invalid" },
    status: 401,
  });
  await api("session/register", {
    method: "POST",
    body: {
      login: editor.user.login,
      name: "Повтор",
      password: `Aa1-${randomUUID()}`,
    },
    status: 409,
  });
  await api("auth/audit", { token: reader.accessToken, status: 403 });
  await api("admin/users?page=invalid", {
    token: admin.accessToken,
    status: 400,
  });
  await api("session/unknown-address", {
    token,
    status: 404,
    headers: { Accept: "text/html" },
  });
  await api("session/me", { method: "POST", token, status: 405 });

  await api("catalog/text-works/" + missing, { status: 404 });
  await api("catalog/text-works/invalid-id", { status: 400 });
  await api("library/works", { status: 401 });
  await api("library/works", { token: "invalid", status: 401 });
  await api("library/works/" + missing, { token, status: 404 });
  await api("library/works", {
    method: "POST",
    token: reader.accessToken,
    body: {},
    status: 403,
  });
  await api("library/works", { method: "POST", token, body: {}, status: 400 });
  const collections = await api("library/collections", { token });
  const input = {
    code: `CONTRACT-${run}`,
    title: "Проверка сервисного слоя",
    author: "Тест",
    description: "Временная запись проверки",
    language: "ru",
    collectionId: collections.find((c) => c.active).id,
    status: "DRAFT",
    targetMinutes: 3,
    featured: false,
    tags: [],
    script: "Рассказчик: Проверка.",
    version: 0,
  };
  work = await api("library/works", {
    method: "POST",
    token,
    body: input,
    status: 201,
  });
  await api("library/works", {
    method: "POST",
    token,
    body: input,
    status: 409,
  });
  await api(`library/works/${work.id}`, {
    method: "PUT",
    token,
    body: { ...input, version: work.version + 100 },
    status: 409,
  });
  const note = await api(`library/works/${work.id}/notes`, {
    method: "POST",
    token,
    body: { content: "Проверка заметки", pinned: false },
    status: 201,
  });
  await api(`library/works/${work.id}/notes/${note.id}`, {
    method: "PUT",
    token,
    body: { content: "Исправлено", pinned: true },
    status: 204,
  });
  await api(`library/works/${work.id}/notes/${note.id}`, {
    method: "DELETE",
    token: reader.accessToken,
    status: 403,
  });
  await api(`library/works/${work.id}/notes/${note.id}`, {
    method: "DELETE",
    token: admin.accessToken,
    status: 204,
  });
  const bulk = await api("library/works/bulk", {
    method: "POST",
    token,
    body: { ids: [work.id, missing], operation: "archive" },
  });
  assert.deepEqual(bulk.success, [work.id]);
  assert.equal(bulk.errors.length, 1);
  assert.equal(bulk.errors[0].id, missing);
  work = await api(`library/works/${work.id}`, { token });
  assert.equal(work.archived, true);
  const audit = await api(
    `library/audit?q=${encodeURIComponent("Проверка сервисного слоя")}`,
    { token: admin.accessToken },
  );
  assert.ok(audit.totalElements >= 2);
  checks.push(
    "CRUD, optimistic lock, note editing/deletion, audit and partial bulk success",
  );

  for (const path of [
    "draft-recordings/roles/" + missing,
    "recordings/renders/" + missing,
    "text-work-content/text-works/" + missing,
  ]) {
    await api(path, { token, status: 404 });
    await api(path, {
      status: path.startsWith("text-work-content/") ? 404 : 401,
    });
  }
  await api("recordings/renders", {
    method: "POST",
    token,
    body: {},
    status: 400,
  });
  await api("recordings/renders", {
    method: "POST",
    token: reader.accessToken,
    body: {},
    status: 403,
  });
  await api("draft-recordings/fragments/" + missing, {
    method: "POST",
    token,
    status: 400,
  });
  await api("text-work-content/text-works/not-a-uuid", { token, status: 400 });
  await api("export/statistics", { status: 401 });
  await api("export/xlsx?services=unknown", {
    token: reader.accessToken,
    status: 400,
  });
  await api("export/not-found", { token, status: 404 });
  const statistics = await api("export/statistics", {
    token: reader.accessToken,
  });
  assert.equal(statistics.metrics.length, 5);
  const history = await api("recordings/renders?page=0&size=12", { token });
  assert.ok(Array.isArray(history.content));
  checks.push(
    "Statistics and render history are available through service queries",
  );

  await call("/api/library/works/import/preview", {
    origin: web,
    method: "POST",
    body: {},
    status: 404,
  });
  await call("/api/library/works", {
    origin: web,
    method: "POST",
    headers: { Origin: "https://foreign.invalid" },
    body: {},
    status: 403,
  });
} finally {
  if (work) {
    const current = await api(`library/works/${work.id}`, {
      token: admin.accessToken,
    });
    if (!current.archived)
      await api(`library/works/${work.id}?version=${current.version}`, {
        method: "DELETE",
        token: admin.accessToken,
        status: 204,
      });
    const archived = await api(`library/works/${work.id}`, {
      token: admin.accessToken,
    });
    await api(
      `library/works/${work.id}/permanent?version=${archived.version}`,
      { method: "DELETE", token: admin.accessToken, status: 204 },
    );
  }
  for (const session of [editor, reader].filter(Boolean)) {
    await api("session/logout", {
      method: "POST",
      token: session.accessToken,
      body: { refreshToken: session.refreshToken },
      status: 204,
    });
    await api(`admin/users/${session.user.id}`, {
      method: "PUT",
      token: admin.accessToken,
      body: { ...session.user, active: false, password: "" },
    });
  }
  await api("session/logout", {
    method: "POST",
    token: admin.accessToken,
    body: { refreshToken: admin.refreshToken },
    status: 204,
  });
}
await writeFile(
  new URL("../artifacts/api-contract-verification.json", import.meta.url),
  JSON.stringify(
    {
      checkedAt: new Date().toISOString(),
      checks,
      cleanup: "Own work deleted; test accounts disabled",
    },
    null,
    2,
  ),
);
console.log(`${checks.length} checks passed; own fixtures cleaned`);
