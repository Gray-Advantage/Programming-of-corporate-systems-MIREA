<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/state";
  import { api, post, message, date } from "$lib/api";
  import { session } from "$lib/session.svelte";
  import type { Work } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  import RenderStatus from "$lib/components/RenderStatus.svelte";
  let work = $state<Work | null>(null),
    roles = $state<any[]>([]),
    selections = $state<Record<string, string>>({}),
    outputMode = $state("SINGLE_FILE"),
    error = $state(""),
    busy = $state(false),
    loading = $state(true),
    renderId = $state("");
  async function load() {
    loading = true;
    error = "";
    try {
      [work] = await Promise.all([
        api<Work>(`library/works/${page.params.id}`),
      ]);
      const result = await api(`recordings/text-works/${page.params.id}/roles`);
      roles = result.roles;
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  async function render() {
    busy = true;
    error = "";
    try {
      const job = await post("recordings/renders", {
        textWorkId: page.params.id,
        outputMode,
        roles: roles.map((r) => ({
          roleId: r.roleId,
          roleRecordingId: selections[r.roleId],
        })),
      });
      renderId = job.id;
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  onMount(() => {
    void load();
  });
</script>

<svelte:head
  ><title>Сборка аудио — {work?.title || "Теремок"}</title></svelte:head
>
<a href={`/works/${page.params.id}`} class="back"
  ><Icon name="left" size={14} />К произведению</a
>
<div class="page-heading">
  <div>
    <h1>Сборка аудио</h1>
    <p>{work?.title} — выберите голос для каждой роли</p>
  </div>
  <button class="btn secondary" onclick={load}
    ><Icon name="refresh" size={17} />Обновить роли</button
  >
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}
{#if loading}<div class="skeleton">
    Загружаем опубликованные озвучки…
  </div>{:else}<div class="render-layout">
    <section class="panel">
      <h2>Состав исполнителей</h2>
      <p class="hint">
        В сборку попадают только полностью опубликованные роли. Можно
        использовать озвучки других участников.
      </p>
      {#each roles as role}<label class="role-row"
          ><span
            ><strong>{role.roleName}</strong><small
              >{role.recordings.length
                ? `Опубликовано вариантов: ${role.recordings.length}`
                : "Озвучек пока нет"}</small
            ></span
          ><select
            aria-label={`Озвучка роли ${role.roleName}`}
            value={selections[role.roleId] || ""}
            onchange={(event) =>
              (selections = {
                ...selections,
                [role.roleId]: event.currentTarget.value,
              })}
            ><option value="">Выберите озвучку</option
            >{#each role.recordings as recording}<option value={recording.id}
                >{recording.userId === session.user?.id
                  ? "Моя озвучка"
                  : "Другой участник"} — {date(recording.publishedAt)}</option
              >{/each}</select
          ></label
        >{:else}<div class="empty">
          <h3>Ролей пока нет</h3>
          <p>Произведение обрабатывается или сценарий ещё не добавлен.</p>
        </div>{/each}
    </section>
    <section class="panel render-options">
      <h2>Формат результата</h2>
      <label class="format" class:selected={outputMode === "SINGLE_FILE"}
        ><input type="radio" bind:group={outputMode} value="SINGLE_FILE" /><span
          ><strong>Одно произведение</strong><small
            >Все реплики в одном MP3-файле</small
          ></span
        ></label
      ><label class="format" class:selected={outputMode === "BY_SEGMENTS"}
        ><input type="radio" bind:group={outputMode} value="BY_SEGMENTS" /><span
          ><strong>По частям</strong><small
            >Отдельный MP3 для каждой главы или части</small
          ></span
        ></label
      ><button
        class="btn"
        onclick={render}
        disabled={busy ||
          !roles.length ||
          roles.some((r) => !selections[r.roleId])}
        ><Icon name="headphones" size={17} />{busy
          ? "Создаём сборку…"
          : "Собрать аудио"}</button
      >
      <p class="hint">Выберите вариант для каждой роли, чтобы начать сборку.</p>
    </section>
  </div>
  {#if renderId}<div class="result">
      {#key renderId}<RenderStatus id={renderId} />{/key}
    </div>{/if}{/if}

<style>
  .render-layout {
    display: grid;
    grid-template-columns: 1fr 320px;
    gap: 24px;
    align-items: start;
  }
  .panel > h2 {
    font-size: 17px;
  }
  .role-row {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 16px;
    justify-content: space-between;
    border-top: 1px solid var(--line);
    padding-top: 20px;
    margin-top: 20px;
  }
  .role-row strong,
  .format strong {
    font-size: 12px;
    font-weight: 500;
  }
  .role-row small,
  .format small {
    display: block;
    font-size: 10px;
    line-height: 1.6;
    margin-top: 7px;
  }
  .role-row select {
    font-size: 11px;
    max-width: 270px;
  }
  .format {
    display: flex;
    flex-direction: row;
    align-items: start;
    padding: 16px;
    border: 1px solid var(--line);
    border-radius: 8px;
    margin-bottom: 12px;
  }
  .format input {
    margin-top: 2px;
  }
  .format.selected {
    background: #f5faf5;
    border-color: #a5c1ab;
  }
  .render-options > .btn {
    margin-top: 15px;
    width: 100%;
  }
  .render-options > .hint {
    font-size: 10px;
    margin-top: 15px;
    text-align: center;
  }
  .result {
    margin-top: 24px;
  }
  @media (max-width: 1150px) {
    .render-layout {
      grid-template-columns: 1fr;
    }
    .render-options {
      max-width: 500px;
    }
  }
</style>
