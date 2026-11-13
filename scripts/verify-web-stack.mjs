import assert from "node:assert/strict";
import { randomUUID, randomBytes } from "node:crypto";
import fs from "node:fs/promises";

const base = process.env.BACKEND_URL || "http://127.0.0.1:8080";
const run = randomUUID().slice(0, 8);
const checks = [];
const envPath = new URL("../.env", import.meta.url);
let localEnv = await fs.readFile(envPath, "utf8");
const setting = (key) =>
  localEnv.match(new RegExp(`^${key}=(.*)$`, "m"))?.[1].trim();
async function request(method, path, body, token, expected = 200) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData))
    headers["Content-Type"] = "application/json";
  const r = await fetch(base + "/api/v1/" + path, {
    method,
    headers,
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  const text = await r.text();
  assert.equal(
    r.status,
    expected,
    `${method} ${path}: HTTP ${r.status}, ${text.slice(0, 700)}`,
  );
  return text ? JSON.parse(text) : null;
}
const get = (path, token, status) => request("GET", path, null, token, status);
const post = (path, body, token, status) =>
  request("POST", path, body, token, status);
const password = () => `Teremok${randomBytes(16).toString("hex")}7aA`;
await get("library/works", null, 401);
let adminPassword = setting("BOOTSTRAP_ADMIN_PASSWORD");
let admin = await post("session/login", {
  login: setting("BOOTSTRAP_ADMIN_LOGIN") || "admin",
  password: adminPassword,
});
if (admin.user.mustChangePassword) {
  await get("library/works", admin.accessToken, 403);
  const next = password();
  await post(
    "session/password",
    { currentPassword: adminPassword, newPassword: next },
    admin.accessToken,
    204,
  );
  await post(
    "session/refresh",
    { refreshToken: admin.refreshToken },
    null,
    401,
  );
  localEnv = localEnv.replace(
    /^BOOTSTRAP_ADMIN_PASSWORD=.*$/m,
    `BOOTSTRAP_ADMIN_PASSWORD=${next}`,
  );
  await fs.writeFile(envPath, localEnv);
  adminPassword = next;
  admin = await post("session/login", {
    login: admin.user.login,
    password: next,
  });
}
assert.ok(admin.user.roles.includes("ADMIN"));
const firstRefresh = admin.refreshToken;
admin = await post("session/refresh", { refreshToken: firstRefresh });
await post("session/refresh", { refreshToken: firstRefresh }, null, 401);
const claims = JSON.parse(
  Buffer.from(admin.accessToken.split(".")[1], "base64url").toString(),
);
assert.ok(claims.exp - claims.iat <= 15 * 60);
checks.push(
  "15-minute JWT, forced temporary-password replacement, refresh rotation and revoked-session rejection",
);

const editorPassword = password();
const editor = await post(
  "session/register",
  {
    login: `editor-${run}`,
    name: "Проверка интерфейса",
    password: editorPassword,
  },
  null,
  201,
);
const readerPassword = password();
const readerUser = await post(
  "admin/users",
  {
    login: `reader-${run}`,
    name: "Читатель для проверки",
    roles: ["READER"],
    active: true,
    password: readerPassword,
  },
  admin.accessToken,
  201,
);
let reader = await post("session/login", {
  login: readerUser.login,
  password: readerPassword,
});
const readerNewPassword = password();
await post(
  "session/password",
  { currentPassword: readerPassword, newPassword: readerNewPassword },
  reader.accessToken,
  204,
);
reader = await post("session/login", {
  login: readerUser.login,
  password: readerNewPassword,
});
await get("library/works", reader.accessToken);
await get("admin/users", reader.accessToken, 403);
await get("library/audit", reader.accessToken, 403);
await post("library/works", {}, reader.accessToken, 403);
await request(
  "PUT",
  `admin/users/${readerUser.id}`,
  { ...readerUser, roles: ["READER"], active: false, password: "" },
  admin.accessToken,
);
await post(
  "session/login",
  { login: readerUser.login, password: readerNewPassword },
  null,
  401,
);
await get("session/me", reader.accessToken, 401);
checks.push(
  "Reader/editor/admin authorization and immediate blocking of disabled accounts",
);

const collection = await post(
  "library/collections",
  { name: `Проверка ${run}`, active: true },
  admin.accessToken,
  201,
);
const input = {
  code: `CHECK-${run}`,
  title: `Проверка озвучивания ${run}`,
  author: "Учебный пример",
  description: "Произведение для сквозной проверки API и аудио.",
  language: "rus",
  collectionId: collection.id,
  status: "RECORDING",
  targetMinutes: 4,
  featured: true,
  tags: ["проверка"],
  script: "Рассказчик: Начинается наша история.\nГерой: Я готов рассказать её!",
  version: 0,
};
let work = await post("library/works", input, editor.accessToken, 201);
await post("library/works", input, editor.accessToken, 409);
await post(
  "library/works",
  { ...input, code: `BAD-${run}`, targetMinutes: -1 },
  editor.accessToken,
  400,
);
work = await request(
  "PUT",
  `library/works/${work.id}`,
  { ...input, title: input.title + " — обновлено", version: work.version },
  editor.accessToken,
);
await request(
  "PUT",
  `library/works/${work.id}`,
  { ...input, version: 0 },
  editor.accessToken,
  409,
);
let list = await get(
  `library/works?q=${encodeURIComponent(run)}&featured=true&complexity=LOW&minutes=5&comparison=lt&sort=targetMinutes&size=1`,
  editor.accessToken,
);
assert.equal(list.totalElements, 1);
assert.equal(list.content[0].id, work.id);
await get("library/works?collectionId=not-a-uuid", editor.accessToken, 400);
const note = await post(
  `library/works/${work.id}/notes`,
  { content: "Начальная заметка", pinned: true },
  editor.accessToken,
  201,
);
await request(
  "PUT",
  `library/works/${work.id}/notes/${note.id}`,
  { content: "Обновлённая заметка", pinned: false },
  editor.accessToken,
  204,
);
await get(`library/works/${work.id}/notes`, reader.accessToken, 401);
await request(
  "DELETE",
  `library/works/${work.id}/notes/${note.id}`,
  null,
  admin.accessToken,
  204,
);
const bulk = await post(
  "library/works/bulk",
  { ids: [work.id, randomUUID()], operation: "addTag", value: "массовая" },
  editor.accessToken,
);
assert.equal(bulk.success.length, 1);
assert.equal(bulk.errors.length, 1);
work = await get(`library/works/${work.id}`, editor.accessToken);
assert.ok(work.tags.includes("массовая"));
await request(
  "DELETE",
  `library/works/${work.id}?version=${work.version}`,
  null,
  editor.accessToken,
  204,
);
assert.equal(
  (await get(`library/works?q=${run}`, editor.accessToken)).totalElements,
  0,
);
work = await get(`library/works/${work.id}`, editor.accessToken);
await post(
  `library/works/${work.id}/restore?version=${work.version}`,
  {},
  editor.accessToken,
  204,
);
checks.push(
  "Validated CRUD, conflict 409, server filters/pagination/sorting, notes, archive/restore and partial bulk results",
);

const headers =
  "code,title,author,description,language,collectionId,status,targetMinutes,featured,tags,script\r\n";
const csv =
  headers +
  `CSV-${run},Импорт ${run},Автор,Описание,rus,${collection.id},DRAFT,5,false,импорт,Рассказчик: Проверка импорта.\r\n`;
async function preview(text) {
  const form = new FormData();
  form.append("file", new Blob([text], { type: "text/csv" }), "check.csv");
  form.append("mode", "NEW");
  return post("library/works/import/preview", form, editor.accessToken);
}
const bad = await preview(csv.replace(",5,false,", ",-2,false,"));
assert.equal(bad.errorCount, 1);
assert.equal(bad.canApply, false);
await post(
  "library/works/import/apply",
  { id: bad.id },
  editor.accessToken,
  400,
);
const valid = await preview(csv);
assert.equal(valid.validCount, 1);
assert.equal(
  (await get(`library/works?q=CSV-${run}`, editor.accessToken)).totalElements,
  0,
);
assert.equal(
  (
    await post(
      "library/works/import/apply",
      { id: valid.id },
      editor.accessToken,
    )
  ).imported,
  1,
);
await post(
  "library/works/import/apply",
  { id: valid.id },
  editor.accessToken,
  409,
);
const exportResult = await fetch(
  `${base}/api/v1/library/works/export?q=${encodeURIComponent(`CSV-${run}`)}`,
  { headers: { Authorization: `Bearer ${editor.accessToken}` } },
);
assert.equal(exportResult.status, 200);
assert.ok((await exportResult.text()).includes(`CSV-${run}`));
const summary = await get("library/analytics/summary", editor.accessToken);
assert.ok(Number(summary.totals.total) >= 2);
const audit = await get(
  `library/audit?q=${encodeURIComponent(run)}`,
  admin.accessToken,
);
assert.ok(audit.totalElements > 0);
const swagger = await fetch(base + "/v3/api-docs", {
  headers: { Authorization: `Bearer ${admin.accessToken}` },
});
assert.equal(swagger.status, 200);
assert.ok((await swagger.json()).paths["/api/v1/library/works"]);
const xlsx = await fetch(
  base + "/api/v1/export/xlsx?services=catalog-service",
  { headers: { Authorization: `Bearer ${admin.accessToken}` } },
);
assert.equal(xlsx.status, 200);
assert.ok((await xlsx.arrayBuffer()).byteLength > 1000);
checks.push(
  "CSV preview/apply isolation, errors, replay protection, filtered export, analytics, audit, Swagger and Excel",
);

let content;
for (let i = 0; i < 30; i++) {
  const r = await fetch(
    `${base}/api/v1/text-work-content/text-works/${work.id}`,
  );
  if (r.ok) {
    content = await r.json();
    break;
  }
  await new Promise((resolve) => setTimeout(resolve, 1000));
}
assert.ok(content, "Text-work event did not reach content service");
const samples = 4410,
  wav = Buffer.alloc(44 + samples * 2);
wav.write("RIFF");
wav.writeUInt32LE(wav.length - 8, 4);
wav.write("WAVEfmt ", 8);
wav.writeUInt32LE(16, 16);
wav.writeUInt16LE(1, 20);
wav.writeUInt16LE(1, 22);
wav.writeUInt32LE(44100, 24);
wav.writeUInt32LE(88200, 28);
wav.writeUInt16LE(2, 32);
wav.writeUInt16LE(16, 34);
wav.write("data", 36);
wav.writeUInt32LE(samples * 2, 40);
for (let i = 0; i < samples; i++)
  wav.writeInt16LE(
    Math.round(Math.sin((i * 2 * Math.PI * 440) / 44100) * 5000),
    44 + i * 2,
  );
const published = [];
for (const role of content.voiceParts) {
  const selections = [];
  for (const fragment of content.segments
    .flatMap((s) => s.fragments)
    .filter((f) => f.voicePartId === role.id)) {
    const form = new FormData();
    form.append("file", new Blob([wav], { type: "audio/wav" }), "Проверка.wav");
    const draft = await post(
      `draft-recordings/fragments/${fragment.id}`,
      form,
      editor.accessToken,
      201,
    );
    const audio = await fetch(
      `${base}/api/v1/draft-recordings/${draft.id}/audio`,
      { headers: { Authorization: `Bearer ${editor.accessToken}` } },
    );
    assert.equal(audio.status, 200);
    assert.equal((await audio.arrayBuffer()).byteLength, wav.length);
    selections.push({ fragmentId: fragment.id, draftRecordingId: draft.id });
  }
  const result = await post(
    `draft-recordings/roles/${role.id}/publish`,
    { selections },
    editor.accessToken,
    202,
  );
  published.push({ roleId: role.id, roleRecordingId: result.roleRecordingId });
}
let publishedReady = false;
for (let i = 0; i < 20; i++) {
  const r = await get(
    `recordings/text-works/${work.id}/roles`,
    editor.accessToken,
  );
  if (r.roles.length && r.roles.every((x) => x.recordings.length)) {
    publishedReady = true;
    break;
  }
  await new Promise((resolve) => setTimeout(resolve, 1000));
}
assert.ok(publishedReady);
const renderIds = [];
for (const outputMode of ["SINGLE_FILE", "BY_SEGMENTS"]) {
  let job = await post(
    "recordings/renders",
    { textWorkId: work.id, outputMode, roles: published },
    editor.accessToken,
    202,
  );
  for (
    let i = 0;
    i < 60 && !["COMPLETED", "FAILED"].includes(job.status);
    i++
  ) {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    job = await get(`recordings/renders/${job.id}`, editor.accessToken);
  }
  assert.equal(job.status, "COMPLETED", job.error);
  assert.ok(job.outputs.length);
  const audio = await fetch(
    `${base}/api/v1/recordings/renders/${job.id}/outputs/0`,
    { headers: { Authorization: `Bearer ${editor.accessToken}` } },
  );
  assert.equal(audio.status, 200);
  assert.ok((await audio.arrayBuffer()).byteLength > 100);
  renderIds.push(job.id);
}
const history = await get("recordings/renders", editor.accessToken);
assert.equal(history.totalElements, 2);
await post(
  "session/logout",
  { refreshToken: editor.refreshToken },
  editor.accessToken,
  204,
);
await post("session/refresh", { refreshToken: editor.refreshToken }, null, 401);
checks.push(
  "Real WAV upload/download, Kafka role publication, FFmpeg MP3 rendering in both modes, history and logout revocation",
);

await fs.mkdir(new URL("../artifacts/", import.meta.url), { recursive: true });
await fs.writeFile(new URL("../artifacts/voice.wav", import.meta.url), wav);
await fs.writeFile(
  new URL("../artifacts/web-verification.json", import.meta.url),
  JSON.stringify(
    {
      run,
      checkedAt: new Date().toISOString(),
      workId: work.id,
      renderIds,
      checks,
    },
    null,
    2,
  ),
);
console.log(JSON.stringify({ run, workId: work.id, checks }, null, 2));
