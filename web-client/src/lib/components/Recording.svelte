<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import { beforeNavigate } from "$app/navigation";
  import { api, post, remove, message, date, plural } from "$lib/api";
  import { canEdit, session } from "$lib/session.svelte";
  import type { Work, Content, Draft } from "$lib/types";
  import { wavFromRecording } from "$lib/audio";
  import Icon from "./Icon.svelte";
  let { id }: { id: string } = $props();
  let work = $state<Work | null>(null),
    content = $state<Content | null>(null),
    roleId = $state(""),
    fragmentId = $state(""),
    drafts = $state<Record<string, Draft[]>>({}),
    selections = $state<Record<string, string>>({}),
    error = $state(""),
    notice = $state(""),
    loading = $state(true),
    busy = $state(false),
    recording = $state(false),
    seconds = $state(0),
    audioUrl = $state(""),
    pending = $state<File | null>(null),
    pendingFragment = $state(""),
    pendingDelete = $state(""),
    confirmPublish = $state(false);
  let recorder: MediaRecorder | null = null,
    stream: MediaStream | null = null,
    timer: ReturnType<typeof setInterval> | undefined,
    closed = false;
  const fragments = $derived(
    content?.segments.flatMap((s) =>
      s.fragments
        .filter((f) => f.voicePartId === roleId)
        .map((f) => ({ ...f, segment: s.name })),
    ) || [],
  );
  const fragment = $derived(fragments.find((f) => f.id === fragmentId));
  const chosen = $derived(fragments.filter((f) => selections[f.id]).length);
  function storageKey() {
    return `teremok-takes:${session.user?.id}:${roleId}`;
  }
  function store() {
    try {
      localStorage.setItem(storageKey(), JSON.stringify(selections));
    } catch {
      /* Storage may be unavailable. */
    }
  }
  async function refresh() {
    if (!roleId) return;
    const result = await api(`draft-recordings/roles/${roleId}`);
    const rows: Record<string, Draft[]> = {};
    for (const f of result.fragments) rows[f.fragmentId] = f.recordings;
    drafts = rows;
    selections = Object.fromEntries(
      Object.entries(selections).filter(([fid, did]) =>
        rows[fid]?.some((d) => d.id === did),
      ),
    );
    store();
  }
  async function changeRole() {
    selections = {};
    try {
      selections = JSON.parse(localStorage.getItem(storageKey()) || "{}");
    } catch {
      selections = {};
    }
    fragmentId = fragments[0]?.id || "";
    error = "";
    try {
      await refresh();
    } catch (e) {
      error = message(e);
    }
  }
  async function load() {
    loading = true;
    error = "";
    try {
      [work, content] = await Promise.all([
        api<Work>(`library/works/${id}`),
        api<Content>(`text-work-content/text-works/${id}`),
      ]);
      roleId = content.voiceParts[0]?.id || "";
      await changeRole();
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  async function upload(file: File, fid = fragmentId) {
    if (!file.size || file.size > 100 * 1024 * 1024) {
      error = "Размер аудио должен быть от 1 байта до 100 МБ";
      return;
    }
    busy = true;
    error = "";
    try {
      const data = new FormData();
      data.append("file", file);
      await post(`draft-recordings/fragments/${fid}`, data);
      pending = null;
      await refresh();
      notice = "Вариант записи сохранён. Выберите его для публикации.";
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  async function start() {
    error = "";
    notice = "";
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
      error =
        "Запись с микрофона недоступна. Откройте сайт через localhost или HTTPS либо загрузите аудиофайл.";
      return;
    }
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      recorder = new MediaRecorder(stream);
      const chunks: BlobPart[] = [];
      const fid = fragmentId;
      const type = recorder.mimeType;
      recorder.ondataavailable = (event) => {
        if (event.data.size) chunks.push(event.data);
      };
      recorder.onstop = () => {
        stream?.getTracks().forEach((t) => t.stop());
        stream = null;
        if (closed) return;
        busy = true;
        void (async () => {
          try {
            const wav = await wavFromRecording(new Blob(chunks, { type }));
            pending = new File(
              [wav],
              `Запись ${new Date().toISOString().replaceAll(":", "-")}.wav`,
              { type: "audio/wav" },
            );
            pendingFragment = fid;
            await upload(pending, fid);
          } catch (e) {
            error = "Не удалось сохранить запись. " + message(e);
          } finally {
            busy = false;
          }
        })();
      };
      recorder.start();
      recording = true;
      seconds = 0;
      timer = setInterval(() => {
        seconds++;
        if (seconds >= 120) stop();
      }, 1000);
    } catch {
      stream?.getTracks().forEach((t) => t.stop());
      stream = null;
      error =
        "Нет доступа к микрофону. Разрешите его в настройках браузера или загрузите готовое аудио.";
    }
  }
  function stop() {
    if (timer) clearInterval(timer);
    recording = false;
    if (recorder?.state === "recording") recorder.stop();
  }
  function selectDraft(draft: Draft) {
    selections = { ...selections, [fragmentId]: draft.id };
    store();
  }
  async function preview(draft: Draft) {
    try {
      if (audioUrl) URL.revokeObjectURL(audioUrl);
      const response = await fetch(`/api/draft-recordings/${draft.id}/audio`);
      if (!response.ok) throw new Error("Не удалось загрузить аудио");
      const blob = await response.blob();
      audioUrl = URL.createObjectURL(
        new Blob([blob], {
          type: draft.originalFileName.toLowerCase().endsWith(".wav")
            ? "audio/wav"
            : blob.type,
        }),
      );
    } catch (e) {
      error = message(e);
    }
  }
  async function del() {
    busy = true;
    try {
      await remove(`draft-recordings/${pendingDelete}`);
      pendingDelete = "";
      await refresh();
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  async function publish() {
    busy = true;
    error = "";
    try {
      await post(`draft-recordings/roles/${roleId}/publish`, {
        selections: fragments.map((f) => ({
          fragmentId: f.id,
          draftRecordingId: selections[f.id],
        })),
      });
      confirmPublish = false;
      selections = {};
      await refresh();
      notice =
        "Роль опубликована. Через несколько секунд она станет доступна для сборки аудио.";
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  function beforeUnload(event: BeforeUnloadEvent) {
    if (recording) {
      event.preventDefault();
      event.returnValue = "";
    }
  }
  beforeNavigate(({ cancel }) => {
    if (recording) {
      cancel();
      error = "Сначала остановите запись, чтобы не потерять её.";
    }
  });
  onMount(() => {
    void load();
    window.addEventListener("beforeunload", beforeUnload);
  });
  onDestroy(() => {
    closed = true;
    if (timer) clearInterval(timer);
    if (recorder?.state === "recording") recorder.stop();
    stream?.getTracks().forEach((t) => t.stop());
    if (audioUrl) URL.revokeObjectURL(audioUrl);
    if (typeof window !== "undefined")
      window.removeEventListener("beforeunload", beforeUnload);
  });
</script>

<svelte:head
  ><title>Запись роли — {work?.title || "Теремок"}</title></svelte:head
>
<a href={`/works/${id}`} class="back"
  ><Icon name="left" size={14} />К произведению</a
>
<div class="page-heading">
  <div>
    <h1>Запись роли</h1>
    <p>{work?.title || "Загружаем произведение…"}</p>
  </div>
  <a href={`/works/${id}/render`} class="btn secondary"
    ><Icon name="headphones" size={17} />К сборке аудио</a
  >
</div>
{#if error}<div class="error" role="alert">
    {error}{#if !content}<button class="btn ghost" onclick={load}
        >Повторить</button
      >{/if}
  </div>{/if}{#if notice}<div class="success" role="status">{notice}</div>{/if}
{#if loading}<div class="skeleton">
    Готовим студию…
  </div>{:else if !canEdit()}<div class="notice">
    Запись и публикация доступны редактору и администратору.
  </div>{:else if content && !content.voiceParts.length}<div class="empty">
    <Icon name="mic" size={32} />
    <h2>В произведении пока нет ролей</h2>
    <p>Добавьте текст сценария в карточке произведения.</p>
  </div>{:else if content}<div class="recording-top panel">
    <label class="role-select"
      >Роль<select
        bind:value={roleId}
        onchange={changeRole}
        disabled={recording || busy}
        >{#each content.voiceParts as role}<option value={role.id}
            >{role.name} — {plural(
              role.totalFragmentsCount,
              "реплика",
              "реплики",
              "реплик",
            )}</option
          >{/each}</select
      ></label
    >
    <div class="progress-block">
      <div class="row between">
        <span>Готовность роли</span><strong
          >{chosen} из {fragments.length}</strong
        >
      </div>
      <progress value={chosen} max={Math.max(1, fragments.length)}></progress>
    </div>
    <button
      class="btn"
      disabled={busy ||
        recording ||
        !fragments.length ||
        chosen !== fragments.length}
      onclick={() => (confirmPublish = true)}>Опубликовать роль</button
    >
  </div>
  {#if confirmPublish}<div class="confirm">
      <p>
        Опубликовать выбранные дубли всей роли? Другие участники смогут
        использовать озвучку в своих сборках.
      </p>
      <button class="btn" onclick={publish} disabled={busy}>Опубликовать</button
      ><button class="btn secondary" onclick={() => (confirmPublish = false)}
        >Отмена</button
      >
    </div>{/if}
  <div class="recording-layout">
    <section class="panel fragment-list">
      <div class="row between">
        <h2>Реплики</h2>
        <span class="muted">{fragments.length}</span>
      </div>
      {#each fragments as f, i}<button
          class:active={f.id === fragmentId}
          class="fragment"
          disabled={recording || busy}
          onclick={() => {
            fragmentId = f.id;
            audioUrl = "";
          }}
          ><span class="fragment-number">{String(i + 1).padStart(2, "0")}</span
          ><span class="fragment-description"
            ><span>{f.content}</span><small>{f.segment}</small></span
          >{#if selections[f.id]}<Icon name="check" size={17} />{/if}</button
        >{/each}
    </section>
    <div class="recording-main">
      <section class="panel">
        <div class="row between">
          <h2>Текст реплики</h2>
          <span class="muted fragment-counter"
            >{fragments.findIndex((f) => f.id === fragmentId) + 1} / {fragments.length}</span
          >
        </div>
        <blockquote>{fragment?.content}</blockquote>
        <div class="record-controls">
          {#if recording}<button class="btn danger" onclick={stop}
              ><Icon name="stop" size={17} />Остановить запись</button
            ><span class="timer"
              ><span></span>{Math.floor(seconds / 60)}:{String(
                seconds % 60,
              ).padStart(2, "0")}</span
            >{:else}<button
              class="btn"
              onclick={start}
              disabled={busy || !fragmentId}
              ><Icon name="mic" size={17} />Записать</button
            ><label class="btn secondary upload-label"
              ><Icon name="upload" size={17} />Загрузить аудио<input
                type="file"
                accept="audio/*,.wav,.mp3,.m4a,.flac,.ogg"
                disabled={busy || !fragmentId}
                onchange={(event) => {
                  const file = event.currentTarget.files?.[0];
                  if (file) void upload(file);
                  event.currentTarget.value = "";
                }}
              /></label
            >{/if}<span class="hint"
            >{recording
              ? "Максимум 2 минуты"
              : "Запись до 2 минут · Файл до 100 МБ"}</span
          >
        </div>
        {#if busy}<p class="hint saving" role="status">
            Сохраняем изменения…
          </p>{/if}{#if pending && !busy}<div class="notice">
            Запись пока не сохранена.<button
              class="btn ghost"
              onclick={() => pending && upload(pending, pendingFragment)}
              >Повторить загрузку</button
            >
          </div>{/if}
      </section>
      <section class="panel takes">
        <div class="row between">
          <h2>Варианты записи</h2>
          <button
            class="icon-btn"
            aria-label="Обновить варианты"
            disabled={busy || recording}
            onclick={() => {
              void refresh().catch((e) => (error = message(e)));
            }}><Icon name="refresh" size={17} /></button
          >
        </div>
        <p class="hint">
          Выберите один дубль этой реплики для публикации роли.
        </p>
        {#if pendingDelete}<div class="confirm">
            <p>Удалить вариант записи?</p>
            <button class="btn danger" onclick={del} disabled={busy}
              >Удалить</button
            ><button class="btn secondary" onclick={() => (pendingDelete = "")}
              >Отмена</button
            >
          </div>{/if}{#each drafts[fragmentId] || [] as draft, i}<div
            class="take"
            class:chosen={selections[fragmentId] === draft.id}
          >
            <label class="take-label"
              ><input
                type="radio"
                name="take"
                checked={selections[fragmentId] === draft.id}
                onchange={() => selectDraft(draft)}
                disabled={recording || busy}
              /><span
                ><strong>Дубль {i + 1}</strong><small
                  >{draft.originalFileName}</small
                ><small
                  >{date(draft.createdAt)} · {(draft.sizeBytes / 1024).toFixed(
                    0,
                  )} КБ</small
                ></span
              ></label
            ><button
              class="icon-btn"
              aria-label={`Прослушать дубль ${i + 1}`}
              disabled={recording}
              onclick={() => preview(draft)}
              ><Icon name="play" size={17} /></button
            ><button
              class="icon-btn"
              aria-label={`Удалить дубль ${i + 1}`}
              disabled={busy || recording}
              onclick={() => (pendingDelete = draft.id)}
              ><Icon name="trash" size={17} /></button
            >
          </div>{:else}<div class="no-takes">
            <Icon name="mic" size={28} />
            <p>Вариантов пока нет</p>
            <small>Запишите реплику или загрузите готовый файл</small>
          </div>{/each}{#if audioUrl}<audio
            controls
            autoplay
            src={audioUrl}
            aria-label="Прослушивание выбранного дубля"
          ></audio>{/if}
      </section>
    </div>
  </div>{/if}

<style>
  .recording-top {
    display: flex;
    align-items: center;
    gap: 28px;
    margin-bottom: 24px;
    padding: 20px 24px;
  }
  .role-select {
    min-width: 210px;
    max-width: 330px;
    font-size: 11px;
  }
  .progress-block {
    flex: 1;
    max-width: 380px;
    margin-left: auto;
  }
  .progress-block .row {
    font-size: 11px;
    color: #829087;
  }
  .progress-block strong {
    font-size: 11px;
    color: #5d7562;
    font-weight: 500;
  }
  progress {
    width: 100%;
    height: 5px;
    appearance: none;
    display: block;
    margin-top: 12px;
  }
  progress::-webkit-progress-bar {
    background: #edf1ee;
    border-radius: 5px;
  }
  progress::-webkit-progress-value {
    background: #78a983;
    border-radius: 5px;
  }
  .recording-layout {
    display: grid;
    grid-template-columns: 290px 1fr;
    gap: 24px;
  }
  .fragment-list {
    padding: 20px 0;
    align-self: start;
    max-height: 690px;
    overflow-y: auto;
  }
  .fragment-list > .row {
    padding: 0 20px 18px;
  }
  .fragment-list h2,
  .recording-main h2 {
    font-size: 16px;
  }
  .fragment-list > .row > span,
  .fragment-counter {
    font-size: 10px;
  }
  .fragment {
    display: flex;
    align-items: start;
    gap: 10px;
    width: 100%;
    padding: 16px 17px;
    text-align: left;
    border-left: 3px solid transparent;
    border-bottom: 1px solid #f0f3f0;
    color: #839087;
  }
  .fragment.active {
    background: #edf5ef;
    border-left-color: var(--green);
    color: #426b4b;
  }
  .fragment-number {
    font-size: 10px;
    color: #a0aca2;
    padding-top: 3px;
  }
  .fragment-description {
    flex: 1;
    min-width: 0;
  }
  .fragment-description > span {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    font-size: 11px;
    line-height: 1.7;
  }
  .fragment-description small {
    display: block;
    font-size: 8px;
    margin-top: 6px;
    color: #a1aba3;
  }
  .fragment :global(svg) {
    margin-top: 2px;
    color: #73a17e;
  }
  .recording-main {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  blockquote {
    font-family: Georgia, serif;
    font-size: 22px;
    line-height: 1.7;
    color: #4b5d50;
    background: #f7f9f5;
    border-radius: 8px;
    padding: 28px;
    margin: 22px 0;
    min-height: 150px;
  }
  .record-controls {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }
  .record-controls .hint {
    font-size: 9px;
    margin-left: auto;
  }
  .upload-label {
    flex-direction: row;
    font-weight: 500;
    position: relative;
    overflow: hidden;
    cursor: pointer;
  }
  .upload-label input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
    width: 100%;
  }
  .timer {
    display: flex;
    align-items: center;
    gap: 6px;
    font-variant-numeric: tabular-nums;
    color: #a04c40;
    font-size: 12px;
  }
  .timer span {
    height: 6px;
    width: 6px;
    border-radius: 50%;
    background: #bc5a4d;
  }
  .saving {
    margin-top: 15px;
  }
  .take {
    display: flex;
    align-items: center;
    gap: 8px;
    border: 1px solid var(--line);
    border-radius: 8px;
    padding: 14px;
    margin-top: 12px;
  }
  .take.chosen {
    border-color: #9abb9f;
    background: #f7faf7;
  }
  .take-label {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 12px;
    min-width: 0;
    flex: 1;
  }
  .take-label > span {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }
  .take-label strong {
    font-size: 11px;
    font-weight: 500;
  }
  .take-label small {
    font-size: 9px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .no-takes {
    padding: 35px 20px;
    text-align: center;
    color: #acb6ad;
  }
  .no-takes p {
    font-size: 12px;
    margin: 10px 0 5px;
    color: #7a887d;
  }
  .no-takes small {
    font-size: 10px;
  }
  .takes > .hint {
    margin: 15px 0 18px;
  }
  audio {
    width: 100%;
    height: 36px;
    margin-top: 20px;
  }
  @media (max-width: 1100px) {
    .recording-layout {
      grid-template-columns: 220px 1fr;
    }
    .recording-top {
      gap: 16px;
      flex-wrap: wrap;
    }
    .recording-top .btn {
      margin-left: auto;
    }
    .progress-block {
      min-width: 160px;
    }
    blockquote {
      font-size: 19px;
      padding: 20px;
    }
  }
</style>
