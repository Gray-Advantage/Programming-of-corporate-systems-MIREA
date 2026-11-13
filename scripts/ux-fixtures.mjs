// Temporary, isolated data for manual browser checks. Never touches existing books.
import { readFile, writeFile, mkdir } from "node:fs/promises";
const base = "http://localhost:5173/api/";
const env = Object.fromEntries((await readFile(new URL("../.env", import.meta.url), "utf8")).split(/\r?\n/).filter(l => l.includes("=")).map(l => [l.slice(0,l.indexOf("=")), l.slice(l.indexOf("=")+1)]));
let cookie = "";
async function request(path, method = "GET", data) {
  const response = await fetch(base + path, { method, headers: { "Content-Type": "application/json", Origin: "http://localhost:5173", Cookie: cookie }, body: data ? JSON.stringify(data) : undefined });
  if (path === "session/login") cookie = response.headers.getSetCookie().map(s => s.split(";")[0]).join("; ");
  if (!response.ok) { const body = await response.json().catch(()=>({})); throw new Error(`${response.status}: ${body.message || path}`); }
  return response.status === 204 ? undefined : response.json();
}
await request("session/login", "POST", { login: env.BOOTSTRAP_ADMIN_LOGIN || "admin", password: env.BOOTSTRAP_ADMIN_PASSWORD });
const file = new URL("../artifacts/ux-fixtures.json", import.meta.url);
const action = process.argv[2] || "create";
if (action === "create") {
  const collection = (await request("library/collections")).find(c=>c.active);
  const works = [];
  const prefix = `UX-POLISH-${Date.now()}`;
  await mkdir(new URL("../artifacts/", import.meta.url), { recursive:true });
  for (let i=1; i<=13; i++) {
    works.push(await request("library/works", "POST", { code: `${prefix}-${i}`, title:`Проверка интерфейса ${String(i).padStart(2,"0")}`, author:"Тестовые данные UX", description:"Временная книга для проверки множественного выбора", collectionId:collection.id, language:"rus", status:"DRAFT", targetMinutes:3, tags:[], featured:false, script:"", version:0 }));
    await writeFile(file, JSON.stringify({ prefix, works }, null, 2));
  }
  console.log(JSON.stringify({ prefix, created:works.length }));
} else {
  const fixture = JSON.parse(await readFile(file,"utf8"));
  const works = action === "remove-first" ? fixture.works.slice(0,1) : fixture.works;
  let deleted=0;
  for (const item of works) {
    let work;
    try { work = await request(`library/works/${item.id}`); } catch(e) { if (e.message.startsWith("404:")) continue; throw e; }
    if (!work.code.startsWith(fixture.prefix)) throw new Error("Fixture ownership mismatch");
    if (!work.archived) { await request(`library/works/${item.id}?version=${work.version}`, "DELETE"); work = await request(`library/works/${item.id}`); }
    await request(`library/works/${item.id}/permanent?version=${work.version}`, "DELETE"); deleted++;
  }
  console.log(JSON.stringify({ action, deleted }));
}
await request("session/logout", "POST");
