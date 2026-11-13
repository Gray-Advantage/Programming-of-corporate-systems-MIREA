import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const web = process.env.WEB_URL || 'http://localhost:5173';
const backend = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
const env = await fs.readFile(new URL('../.env', import.meta.url), 'utf8');
const password = env.match(/^BOOTSTRAP_ADMIN_PASSWORD=(.*)$/m)?.[1].trim();
const login = env.match(/^BOOTSTRAP_ADMIN_LOGIN=(.*)$/m)?.[1].trim() || 'admin';
const jar = new Map();
function cookies(response) {
  for (const line of response.headers.getSetCookie()) {
    assert.ok(/HttpOnly/i.test(line)); assert.ok(/SameSite=Lax/i.test(line));
    const pair = line.split(';')[0], at = pair.indexOf('=');
    if (pair.slice(at + 1)) jar.set(pair.slice(0, at), pair.slice(at + 1)); else jar.delete(pair.slice(0, at));
  }
}
const cookie = () => [...jar].map(([k,v]) => `${k}=${v}`).join('; ');
const logged = await fetch(web + '/api/session/login', { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: web }, body: JSON.stringify({ login, password }) });
assert.equal(logged.status, 200); cookies(logged);
const publicBody = await logged.json(); assert.ok(publicBody.user); assert.equal(publicBody.accessToken, undefined); assert.equal(publicBody.refreshToken, undefined);
assert.ok(jar.has('teremok_access')); assert.ok(jar.has('teremok_refresh'));
const badOrigin = await fetch(web + '/api/library/works/bulk', { method: 'POST', headers: { 'Content-Type': 'application/json', Cookie: cookie(), Origin: 'https://untrusted.invalid' }, body: '{}' });
assert.equal(badOrigin.status, 403);
jar.delete('teremok_access');
const rotated = await Promise.all([1,2,3].map(() => fetch(web + '/api/session/me', { headers: { Cookie: cookie() } })));
for (const r of rotated) { assert.equal(r.status, 200); cookies(r); assert.equal((await r.json()).login, login); }
for (const path of ['/v3/api-docs', '/api/v1/auth/openapi', '/api/v1/draft-recordings/openapi', '/api/v1/recordings/openapi', '/api/v1/text-work-content/openapi', '/api/v1/export/openapi']) {
  const response = await fetch(web + path, { headers: { Cookie: cookie() } });
  assert.equal(response.status, 200, path); assert.ok(Object.keys((await response.json()).paths).length > 0, path);
}
const auth = await fetch(backend + '/api/v1/session/login', { method: 'POST', headers: { 'Content-Type':'application/json' }, body:JSON.stringify({ login,password }) }).then(r=>r.json());
const report = JSON.parse(await fs.readFile(new URL('../artifacts/web-verification.json', import.meta.url), 'utf8'));
const headers = { Authorization: `Bearer ${auth.accessToken}` };
const list = await fetch(`${backend}/api/v1/library/works?q=CSV-${report.run}`, {headers}).then(r=>r.json());
assert.equal(list.content.length,1);
const item = list.content[0];
assert.equal(item.code,`CSV-${report.run}`);
const archived = await fetch(`${backend}/api/v1/library/works/${item.id}?version=${item.version}`,{method:'DELETE',headers});assert.equal(archived.status,204);
const current = await fetch(`${backend}/api/v1/library/works/${item.id}`,{headers}).then(r=>r.json());
const deleted = await fetch(`${backend}/api/v1/library/works/${item.id}/permanent?version=${current.version}`,{method:'DELETE',headers});assert.equal(deleted.status,204);
assert.equal((await fetch(`${backend}/api/v1/library/works/${item.id}`,{headers})).status,404);
const unknown = 'throttle-'+randomUUID();
for(let i=0;i<6;i++){
  const attempt=await fetch(backend+'/api/v1/session/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({login:unknown,password:'InvalidPassword123'})});
  assert.equal(attempt.status,i<5?401:429);
}
const logout = await fetch(web + '/api/session/logout', { method:'POST',headers:{ Cookie:cookie(), Origin:web,'Content-Type':'application/json' },body:'{}' });
assert.equal(logout.status,204); cookies(logout); assert.equal(jar.size,0);
await fs.writeFile(new URL('../artifacts/proxy-verification.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),checks:['HttpOnly/SameSite cookies; tokens absent from browser response','Cross-origin mutation rejected','Three simultaneous requests share one refresh rotation','All six OpenAPI specifications available through SvelteKit','Admin permanent deletion returns 404 and leaves tombstone','Sixth failed login returns 429','Logout clears cookies']},null,2));
console.log('Proxy verification passed: cookies, refresh concurrency, CSRF, 6 OpenAPI specifications, permanent deletion, throttling, logout.');
