<script lang="ts">
  import { onMount, untrack } from "svelte";
  import { page } from "$app/state";
  import { goto } from "$app/navigation";
  import {
    api,
    post,
    remove,
    date,
    message,
    statusLabels,
    plural,
  } from "$lib/api";
  import { canEdit, isAdmin } from "$lib/session.svelte";
  import type { Work, Collection } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  import BookCover from "$lib/components/BookCover.svelte";
  import Notes from "$lib/components/Notes.svelte";
  import ConfirmDialog from "$lib/components/ConfirmDialog.svelte";
  import { previousCatalog } from "$lib/catalog-context";
  let work = $state<Work | null>(null),
    collection = $state(""),
    error = $state(""),
    confirm = $state(""),
    busy = $state(false),
    notice = $state(""),
    back = $state("/");
  let mounted = $state(false),
    request = 0;
  async function load() {
    error = "";
    const currentRequest = ++request;
    const id = page.params.id;
    try {
      const [result, refs] = await Promise.all([
        api<Work>(`library/works/${id}`),
        api<Collection[]>("library/collections"),
      ]);
      if (currentRequest !== request) return;
      work = result;
      back = previousCatalog(result.archived);
      collection = refs.find((c) => c.id === result.collectionId)?.name || "";
    } catch (e) {
      if (currentRequest === request) error = message(e);
    }
  }
  async function action(operation = confirm) {
    if (!work || busy) return;
    error = "";
    busy = true;
    try {
      const base = `library/works/${work.id}`;
      if (operation === "restore")
        await post(`${base}/restore?version=${work.version}`);
      else
        await remove(
          `${base}${operation === "permanent" ? "/permanent" : ""}?version=${work.version}`,
        );
      confirm = "";
      if (operation === "permanent") await goto(previousCatalog(true));
      else {
        await load();
        notice =
          operation === "restore"
            ? "Произведение возвращено в библиотеку."
            : "Произведение отправлено в архив. Его можно восстановить.";
      }
    } catch (e) {
      confirm = "";
      error = message(e);
    } finally {
      busy = false;
    }
  }
  $effect(() => {
    const id = page.params.id;
    if (mounted)
      untrack(() => {
        work = null;
        notice = "";
        confirm = "";
        void load();
      });
  });
  onMount(() => {
    mounted = true;
    return () => {
      request++;
    };
  });
</script>

<svelte:head
  ><title>{work?.title || "Произведение"} — Теремок</title></svelte:head
>
<a href={back} class="back"
  ><Icon name="left" size={14} />{work?.archived
    ? "В архив"
    : "В библиотеку"}</a
>
{#if error}<div class="error" role="alert">{error}</div>
  <button class="btn secondary" onclick={load}>Повторить</button>{/if}
{#if notice}<div class="success" role="status">{notice}</div>{/if}
{#if work}<div class="work-layout">
    <aside class="work-aside">
      <BookCover title={work.title} author={work.author} large />
      <div class="work-id">{work.code}</div>
      {#if !work.archived}<a href={`/works/${work.id}/record`} class="btn"
          ><Icon name="mic" size={17} />Озвучить роль</a
        ><a href={`/works/${work.id}/render`} class="btn secondary"
          ><Icon name="headphones" size={17} />Собрать аудио</a
        >{/if}{#if canEdit()}<a
          href={`/works/${work.id}/edit`}
          class="btn ghost"><Icon name="edit" size={16} />Редактировать</a
        >{/if}
    </aside>
    <div class="work-main">
      <div class="row wrap work-badges">
        <span class={`badge ${work.status}`}>{statusLabels[work.status]}</span
        >{#if work.archived}<span class="badge">В архиве</span
          >{/if}{#if work.featured}<span class="badge READY">Рекомендуем</span
          >{/if}<span class="muted collection">{collection}</span>
      </div>
      <h1>{work.title}</h1>
      <p class="author">{work.author}</p>
      <div class="work-data">
        <div>
          <small>Плановая длительность</small><span
            >{work.targetMinutes} мин.</span
          >
        </div>
        <div>
          <small>Ролей для озвучивания</small><span>{work.rolesCount}</span>
        </div>
        <div>
          <small>Сложность</small><span>{statusLabels[work.complexity]}</span>
        </div>
        <div>
          <small>Язык</small><span
            >{{
              rus: "Русский",
              eng: "Английский",
              deu: "Немецкий",
              fra: "Французский",
            }[work.language] || work.language}</span
          >
        </div>
      </div>
      {#if work.description}<p class="description">
          {work.description}
        </p>{/if}{#if work.tags.length}<div class="row wrap tags">
          {#each work.tags as tag}<span class="tag">{tag}</span>{/each}
        </div>{/if}
      <div class="panel script-preview">
        <div class="row between">
          <h2>Текст произведения</h2>
          <span class="muted"
            >{plural(
              work.script.split("\n").filter(Boolean).length,
              "реплика",
              "реплики",
              "реплик",
            )}</span
          >
        </div>
        {#if work.script}<pre>{work.script}</pre>{:else}<p class="hint">
            Текст пока не добавлен. Редактор может добавить сценарий.
          </p>{/if}
      </div>
      {#key work.id}<Notes id={work.id} />{/key}
      <div class="work-footer">
        <small
          >Добавлено {date(work.createdAt)} · Обновлено {date(work.updatedAt)} · Версия
          {work.version}</small
        >{#if canEdit()}<div class="row">
            {#if work.archived}<button
                class="btn ghost"
                disabled={busy}
                onclick={() => action("restore")}
                >{busy ? "Восстанавливаем…" : "Восстановить"}</button
              >{#if isAdmin()}<button
                  class="btn ghost"
                  onclick={() => (confirm = "permanent")}
                  >Удалить навсегда</button
                >{/if}{:else}<button
                class="btn ghost"
                onclick={() => (confirm = "archive")}
                ><Icon name="archive" size={16} />В архив</button
              >{/if}
          </div>{/if}
      </div>
    </div>
  </div>{:else if !error}<div class="skeleton">
    Открываем произведение…
  </div>{/if}

{#if confirm && work}<ConfirmDialog
    title={confirm === "permanent"
      ? "Удалить произведение навсегда?"
      : "Отправить произведение в архив?"}
    description={confirm === "permanent"
      ? `«${work.title}»: карточка, теги и заметки будут удалены. Отменить это действие нельзя.`
      : `«${work.title}» исчезнет из основной библиотеки. Вернуть произведение можно в разделе «Архив».`}
    label={confirm === "permanent" ? "Удалить навсегда" : "В архив"}
    danger={confirm === "permanent"}
    {busy}
    onconfirm={() => action()}
    onclose={() => (confirm = "")}
  />{/if}

<style>
  .work-layout {
    display: grid;
    grid-template-columns: 224px 1fr;
    gap: 38px;
    align-items: start;
  }
  .work-aside {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .work-id {
    text-align: center;
    color: var(--muted);
    font-size: 10px;
    margin: 6px 0 10px;
  }
  .work-aside .btn {
    font-size: 12px;
  }
  .work-main {
    min-width: 0;
  }
  .work-badges {
    margin: 4px 0 18px;
  }
  .collection {
    font-size: 11px;
  }
  h1 {
    font-size: 34px;
  }
  .author {
    margin-top: 10px;
    color: var(--muted);
    font-size: 13px;
  }
  .work-data {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    border-top: 1px solid var(--line);
    border-bottom: 1px solid var(--line);
    margin: 26px 0;
    padding: 20px 0;
    gap: 15px;
  }
  .work-data small {
    display: block;
    font-size: 11px;
    margin-bottom: 10px;
  }
  .work-data span {
    font-size: 12px;
  }
  .description {
    color: #68766b;
    font-size: 13px;
    white-space: pre-wrap;
  }
  .tags {
    margin: 20px 0;
  }
  .script-preview {
    margin: 26px 0 24px;
  }
  .script-preview h2 {
    font-size: 17px;
  }
  .script-preview .row > span {
    font-size: 10px;
  }
  pre {
    font-family: inherit;
    font-size: 12px;
    line-height: 1.9;
    white-space: pre-wrap;
    max-height: 330px;
    overflow-y: auto;
    color: #53645a;
    margin: 20px 0 0;
    padding-right: 20px;
  }
  .work-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 24px;
    gap: 12px;
    flex-wrap: wrap;
  }
  .work-footer small {
    font-size: 11px;
  }
  @media (max-width: 1100px) {
    .work-layout {
      grid-template-columns: 180px 1fr;
      gap: 24px;
    }
    .work-data {
      grid-template-columns: 1fr 1fr;
    }
    h1 {
      font-size: 28px;
    }
  }
</style>
