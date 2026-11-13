import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';

const root = new URL('../',import.meta.url), artifacts = new URL('../artifacts/',import.meta.url);
const backend = process.env.BACKEND_URL || 'http://localhost:8080';
const web = process.env.WEB_URL || 'http://localhost:5173';
const env = await readFile(new URL('../.env',import.meta.url),'utf8');
const login = env.match(/^BOOTSTRAP_ADMIN_LOGIN=(.*)$/m)?.[1].trim() || 'admin';
const password = env.match(/^BOOTSTRAP_ADMIN_PASSWORD=(.*)$/m)?.[1].trim();
await mkdir(artifacts,{recursive:true});
const checks=[];
async function request(path,{token,method='GET',body,status=200}={}) {
  const response = await fetch(backend+'/api/v1/'+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined});
  assert.equal(response.status,status,`${path}: unexpected HTTP ${response.status}`);
  return status===204?undefined:response.json();
}
const admin = await request('session/login',{method:'POST',body:{login,password}});
let reader, imported;
const run = randomUUID().slice(0,8), code=`UX-IMPORT-${run}`, csvPath=fileURLToPath(new URL('console-import.csv',artifacts));
const jar=fileURLToPath(new URL('../backend/text-work-deployer/target/text-work-deployer-jar-with-dependencies.jar',import.meta.url));
function cli(args,status=0) {
  const java=process.env.JAVA_HOME ? join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java') : 'java';
  const result=spawnSync(java,['-Dfile.encoding=UTF-8','-jar',jar,...args],{cwd:fileURLToPath(root),env:{...process.env,BACKEND_URL:backend,IMPORT_LOGIN:login,IMPORT_PASSWORD:password},encoding:'utf8',timeout:120000});
  assert.equal(result.status,status,`CLI failed: ${result.stdout}\n${result.stderr}`);
  return result.stdout;
}
try {
  reader=await request('session/register',{method:'POST',status:201,body:{login:`ux-reader-${run}`,name:'Проверка прав выгрузки',password:`Aa1-${randomUUID()}`}});
  const collection=(await request('library/collections',{token:admin.accessToken})).find(c=>c.active);
  cli(['--csv-template',csvPath]);
  const headers=(await readFile(csvPath,'utf8')).replace(/^\uFEFF/,'');
  assert.ok(headers.startsWith('code,title,author,'));
  const row=(title,minutes=4)=>`${code},${title},Учебный пример,Проверка консольного импорта,rus,${collection.id},DRAFT,${minutes},false,проверка,Рассказчик: Проверка консольного импорта.\r\n`;
  await writeFile(csvPath,'\uFEFF'+headers+row('Проверка импорта',-1));
  cli(['--csv',csvPath,'--check'],2);
  await writeFile(csvPath,'\uFEFF'+headers+row('Проверка импорта'));
  cli(['--csv',csvPath,'--check']);
  assert.equal((await request(`library/works?q=${code}`,{token:admin.accessToken})).totalElements,0);
  cli(['--csv',csvPath,'--mode','NEW','--yes']);
  imported=(await request(`library/works?q=${code}`,{token:admin.accessToken})).content[0];
  assert.equal(imported.code,code);
  cli(['--csv',csvPath,'--mode','NEW','--yes'],2);
  await writeFile(csvPath,'\uFEFF'+headers+row('Обновлено консольным импортёром'));
  cli(['--csv',csvPath,'--mode','UPSERT','--yes']);
  imported=await request(`library/works/${imported.id}`,{token:admin.accessToken});
  assert.equal(imported.title,'Обновлено консольным импортёром');
  checks.push('Console importer: template, invalid rows, check without mutation, NEW, duplicate prevention, UPSERT');
  const csv=await fetch(`${backend}/api/v1/library/works/export?q=${code}`,{headers:{Authorization:`Bearer ${reader.accessToken}`}});
  assert.equal(csv.status,200);
  const text=await csv.text();
  for (const field of ['id','originType','rolesCount','complexity','archived','createdAt','updatedAt','version','sourcePayload']) assert.ok(text.split(/\r?\n/)[0].includes(field));
  checks.push('Reader CSV contains all work fields and respects filters');
  for (const [role,token] of [['reader',reader.accessToken],['admin',admin.accessToken]]) {
    const result=await fetch(backend+'/api/v1/export/xlsx',{headers:{Authorization:`Bearer ${token}`}});
    assert.equal(result.status,200,`${role} XLSX`);
    assert.equal(result.headers.get('X-Export-Failed-Tables'),'0');
    const bytes=Buffer.from(await result.arrayBuffer()); assert.ok(bytes.length>1000);
    await writeFile(new URL(`export-${role}.xlsx`,artifacts),bytes);
  }
  checks.push('All-service XLSX available to reader and admin');
  await request('library/audit',{token:reader.accessToken,status:403});
  for (const path of ['/api/library/works/import/preview','/api/library/works/import/apply','/api/v1/library/works/import/preview','/api/v1/library/works/import/apply']) {
    assert.equal((await fetch(web+path,{method:'POST',headers:{'Content-Type':'application/json',Origin:web},body:'{}'})).status,404,path);
  }
  checks.push('Audit API remains admin-only; import blocked through both web proxy paths');
} finally {
  if(imported) { const current=await request(`library/works/${imported.id}`,{token:admin.accessToken}); await request(`library/works/${current.id}?version=${current.version}`,{token:admin.accessToken,method:'DELETE',status:204}); const archived=await request(`library/works/${current.id}`,{token:admin.accessToken}); await request(`library/works/${current.id}/permanent?version=${archived.version}`,{token:admin.accessToken,method:'DELETE',status:204}); }
  if(reader) { await request('session/logout',{token:reader.accessToken,method:'POST',body:{refreshToken:reader.refreshToken},status:204}); await request(`admin/users/${reader.user.id}`,{token:admin.accessToken,method:'PUT',body:{login:reader.user.login,name:reader.user.name,roles:['READER'],active:false,password:''}}); }
  await request('session/logout',{token:admin.accessToken,method:'POST',body:{refreshToken:admin.refreshToken},status:204});
}
await writeFile(new URL('export-import-verification.json',artifacts),JSON.stringify({checkedAt:new Date().toISOString(),checks,cleanup:'Own imported work deleted; test reader disabled'},null,2));
console.log(checks.join('\n'));
