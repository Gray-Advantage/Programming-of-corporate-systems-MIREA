import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

// Run against an already started Compose stack. Test records are kept for inspecting the Excel export.
const baseUrl = process.env.BACKEND_URL ?? 'http://127.0.0.1:8080';
const runId = randomUUID();
const workId = randomUUID();
const roles = [randomUUID(), randomUUID()];
const segments = [randomUUID(), randomUUID()];
const fragments = Array.from({ length: 4 }, () => randomUUID());
const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), 'teremok-smoke-'));
const checks = [];
const docker = (...args) => execFileSync('docker', ['compose', ...args], { encoding: 'utf8', timeout: 60_000 });
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

async function api(method, url, body, token, expected = 200) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(baseUrl + url, {
    method, headers, body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  assert.equal(response.status, expected, `${method} ${url}: ${text}`);
  return text ? JSON.parse(text) : null;
}

async function waitFor(description, action) {
  let lastError;
  for (let attempt = 0; attempt < 60; attempt++) {
    try { return await action(); } catch (error) { lastError = error; await sleep(1000); }
  }
  throw new Error(`Timed out: ${description}`, { cause: lastError });
}

async function upload(fragmentId, audio, token) {
  const form = new FormData();
  form.append('file', new Blob([audio], { type: 'audio/wav' }), 'voice.wav');
  return api('POST', `/api/v1/draft-recordings/fragments/${fragmentId}`, form, token, 201);
}

try {
  const event = {
    eventId: randomUUID(), eventVersion: 1, occurredAt: new Date().toISOString(),
    textWork: {
      id: workId, authors: ['Docker smoke test'], name: `Smoke test ${runId}`,
      publicationDate: null, language: 'rus',
      origin: { type: 'ORIGINAL', translatedFrom: null, translators: [] }, segmentType: 'CHAPTER',
      segments: segments.map((id, i) => ({
        id, orderInTextWork: i + 1, name: `Segment ${i + 1}`,
        fragments: roles.map((roleId, j) => ({
          id: fragments[i * 2 + j], orderInSegment: j + 1,
          content: `Test fragment ${i * 2 + j + 1}`, voicePartId: roleId,
        })),
      })),
      voiceParts: roles.map((id, i) => ({ id, name: `Role ${i + 1}`, totalFragmentsCount: 2 })),
    },
  };
  execFileSync('docker', ['compose', 'exec', '-T', 'kafka',
    '/opt/kafka/bin/kafka-console-producer.sh', '--bootstrap-server', 'kafka:9092',
    '--topic', 'text-work-added'], { input: JSON.stringify(event) + '\n', timeout: 30_000 });

  const register = suffix => api('POST', '/api/v1/auth/register', {
    login: `smoke-${runId}-${suffix}`, password: 'smoke-password',
  }, null, 201);
  const author = await register('author');
  const reader = await register('reader');
  await waitFor('text-work projections', async () => {
    const result = await api('GET', `/api/v1/draft-recordings/roles/${roles[0]}`, null, author.accessToken);
    assert.equal(result.fragments.length, 2);
  });
  await api('GET', `/api/v1/draft-recordings/roles/${roles[0]}`, null, null, 401);
  checks.push('JWT required; user and text-work events projected');

  const containerAudio = `/tmp/smoke-${runId}.wav`;
  docker('exec', '-T', 'renderer-service', 'ffmpeg', '-v', 'error', '-y', '-f', 'lavfi',
    '-i', 'sine=frequency=440:duration=0.15', '-c:a', 'pcm_s16le', containerAudio);
  const audioPath = path.join(tempDir, 'voice.wav');
  docker('cp', `renderer-service:${containerAudio}`, audioPath);
  const audio = await fs.readFile(audioPath);
  const selected = [];
  for (const id of fragments) selected.push(await upload(id, audio, author.accessToken));
  for (let i = 1; i < 40; i++) await upload(fragments[0], audio, author.accessToken);
  const before = await api('GET', `/api/v1/draft-recordings/roles/${roles[0]}`, null, author.accessToken);
  assert.equal(before.fragments.find(f => f.fragmentId === fragments[0]).recordings.length, 40);
  await api('DELETE', `/api/v1/draft-recordings/${selected[0].id}`, null, reader.accessToken, 404);
  await api('POST', `/api/v1/draft-recordings/roles/${roles[0]}/publish`, { selections: [] }, author.accessToken, 400);
  checks.push('40 variants per fragment; ownership and complete-role validation');

  const published = [];
  for (let i = 0; i < roles.length; i++) {
    published.push(await api('POST', `/api/v1/draft-recordings/roles/${roles[i]}/publish`, {
      selections: [i, i + 2].map(index => ({ fragmentId: fragments[index], draftRecordingId: selected[index].id })),
    }, author.accessToken, 202));
  }
  const after = await api('GET', `/api/v1/draft-recordings/roles/${roles[0]}`, null, author.accessToken);
  assert.equal(after.fragments.find(f => f.fragmentId === fragments[0]).recordings.length, 39);
  assert.equal(after.fragments.find(f => f.fragmentId === fragments[2]).recordings.length, 0);
  await waitFor('published roles in RecordingsService', async () => {
    const result = await api('GET', `/api/v1/recordings/text-works/${workId}/roles`, null, reader.accessToken);
    assert.equal(result.roles.length, 2);
    for (const role of result.roles) assert.equal(role.recordings.length, 1);
  });
  checks.push('Selected drafts moved; 39 unselected variants remain; published roles consumed');

  const renderJobs = [];
  for (const outputMode of ['SINGLE_FILE', 'BY_SEGMENTS']) {
    const job = await api('POST', '/api/v1/recordings/renders', {
      textWorkId: workId, outputMode,
      roles: roles.map((roleId, i) => ({ roleId, roleRecordingId: published[i].roleRecordingId })),
    }, reader.accessToken, 202);
    const done = await waitFor(`${outputMode} render`, async () => {
      const result = await api('GET', `/api/v1/recordings/renders/${job.id}`, null, reader.accessToken);
      if (result.status === 'FAILED') throw new Error(result.error);
      assert.equal(result.status, 'COMPLETED');
      return result;
    });
    assert.equal(done.outputs.length, outputMode === 'SINGLE_FILE' ? 1 : 2);
    for (const output of done.outputs) {
      assert.equal(output.contentType, 'audio/mpeg');
      assert.ok(output.objectKey.startsWith(`renders/${job.id}/`));
    }
    renderJobs.push(done);
  }
  checks.push('Another user rendered SINGLE_FILE and BY_SEGMENTS through Kafka + FFmpeg');

  const bucket = process.env.S3_BUCKET ?? 'teremok-audio';
  const endpoint = process.env.S3_ENDPOINT ?? 'http://127.0.0.1:19000';
  const credentials = `${process.env.MINIO_ROOT_USER ?? 'teremok'}:${process.env.MINIO_ROOT_PASSWORD ?? 'teremok-secret'}`;
  const download = key => execFileSync(process.platform === 'win32' ? 'curl.exe' : 'curl', [
    '--silent', '--show-error', '--fail', '--aws-sigv4', 'aws:amz:us-east-1:s3',
    '--user', credentials, `${endpoint}/${bucket}/${key}`,
  ], { timeout: 20_000 });
  const sql = `SELECT object_key FROM fragment_recording WHERE role_recording_id IN ('${published[0].roleRecordingId}','${published[1].roleRecordingId}') ORDER BY object_key`;
  const keys = docker('exec', '-T', 'postgres', 'psql', '-U', 'teremok_admin', '-d', 'recordings_db', '-At', '-c', sql)
    .trim().split(/\r?\n/);
  assert.equal(keys.length, 4);
  const hash = data => createHash('sha256').update(data).digest('hex');
  for (const key of keys) assert.equal(hash(download(key)), hash(audio), 'Published source changed');
  for (const job of renderJobs) for (const output of job.outputs) {
    const mp3 = download(output.objectKey);
    assert.ok(mp3.length > 0);
    const localOutput = path.join(tempDir, 'rendered.mp3');
    const containerOutput = `/tmp/smoke-${runId}-rendered.mp3`;
    await fs.writeFile(localOutput, mp3);
    docker('cp', localOutput, `renderer-service:${containerOutput}`);
    const probe = JSON.parse(docker('exec', '-T', 'renderer-service', 'ffprobe', '-v', 'error',
      '-show_streams', '-show_format', '-of', 'json', containerOutput));
    assert.equal(probe.streams[0].codec_name, 'mp3');
    assert.equal(probe.streams[0].sample_rate, '44100');
    assert.equal(probe.streams[0].channels, 2);
    const expectedDuration = job.outputMode === 'SINGLE_FILE' ? 0.6 : 0.3;
    assert.ok(Math.abs(Number(probe.format.duration) - expectedDuration) < 0.15);
  }
  for (const draft of selected) {
    const key = `draft/${author.userId}/${draft.fragmentId}/${draft.id}/voice.wav`;
    const status = execFileSync(process.platform === 'win32' ? 'curl.exe' : 'curl', [
      '--silent', '--output', process.platform === 'win32' ? 'NUL' : '/dev/null', '--write-out', '%{http_code}',
      '--aws-sigv4', 'aws:amz:us-east-1:s3', '--user', credentials, `${endpoint}/${bucket}/${key}`,
    ], { encoding: 'utf8', timeout: 20_000 });
    assert.equal(status, '404', 'Published file still exists under draft/');
  }
  checks.push('S3 originals retain exact SHA-256 and leave draft/; MP3 codec, duration and format verified');

  await fs.mkdir('data/verification', { recursive: true });
  const report = { runId, textWorkId: workId, authorId: author.userId, readerId: reader.userId,
    publishedRoleIds: published.map(p => p.roleRecordingId), renderIds: renderJobs.map(j => j.id), checks };
  await fs.writeFile('data/verification/recordings-smoke.json', JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
} finally {
  await fs.rm(tempDir, { recursive: true, force: true });
}
