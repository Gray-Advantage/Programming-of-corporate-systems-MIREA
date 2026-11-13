<script lang="ts">
  import { onMount } from "svelte";
  import { api, message, date, statusLabels } from "$lib/api";
  import type { Page, Render, Work } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  import RenderStatus from "$lib/components/RenderStatus.svelte";
  let jobs = $state<Render[]>([]),
    names = $state<Record<string, string>>({}),
    pageNumber = $state(0),
    pages = $state(0),
    error = $state(""),
    loading = $state(true),
    selected = $state("");
  async function load() {
    loading = true;
    error = "";
    try {
      const result = await api<Page<Render>>(
        `recordings/renders?page=${pageNumber}&size=12`,
      );
      jobs = result.content;
      pages = result.totalPages;
      const titles = await Promise.all(
        [...new Set(jobs.map((j) => j.textWorkId))].map(async (id) => {
          try {
            const work = await api<Work>(`library/works/${id}`);
            return [id, work.title];
          } catch {
            return [id, "Произведение удалено"];
          }
        }),
      );
      names = Object.fromEntries(titles);
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  onMount(() => {
    void load();
  });
</script>

<svelte:head><title>Мои сборки — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Мои сборки</h1>
    <p>Готовые аудио и задания, отправленные на обработку</p>
  </div>
  <button class="btn secondary" onclick={load}
    ><Icon name="refresh" size={17} />Обновить</button
  >
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}{#if loading}<div
    class="skeleton"
  >
    Загружаем сборки…
  </div>{:else if !jobs.length}<div class="empty">
    <Icon name="headphones" size={34} />
    <h2>Первой сборки пока нет</h2>
    <p>Откройте произведение, выберите озвучки ролей и соберите аудио.</p>
    <a href="/" class="btn secondary">Выбрать произведение</a>
  </div>{:else}<div class="table-wrap">
    <table>
      <thead
        ><tr
          ><th>Произведение</th><th>Создано</th><th>Формат</th><th>Статус</th
          ><th></th></tr
        ></thead
      ><tbody
        >{#each jobs as job}<tr
            ><td
              ><a href={`/works/${job.textWorkId}`}>{names[job.textWorkId]}</a
              ></td
            ><td>{date(job.createdAt)}</td><td
              >{job.outputMode === "SINGLE_FILE"
                ? "Один файл"
                : "По частям"}</td
            ><td
              ><span class={`badge ${job.status}`}
                >{statusLabels[job.status] || "Обрабатывается"}</span
              ></td
            ><td
              ><button class="btn ghost" onclick={() => (selected = job.id)}
                >Открыть</button
              ></td
            ></tr
          >{/each}</tbody
      >
    </table>
  </div>
  <nav class="pagination">
    <button
      class="icon-btn"
      aria-label="Предыдущая страница"
      disabled={!pageNumber}
      onclick={() => {
        pageNumber--;
        void load();
      }}><Icon name="left" size={17} /></button
    ><span>{pageNumber + 1} из {Math.max(pages, 1)}</span><button
      class="icon-btn"
      aria-label="Следующая страница"
      disabled={pageNumber + 1 >= pages}
      onclick={() => {
        pageNumber++;
        void load();
      }}><Icon name="right" size={17} /></button
    >
  </nav>{/if}{#if selected}<div class="render-result">
    {#key selected}<RenderStatus id={selected} />{/key}
  </div>{/if}

<style>
  .render-result {
    margin-top: 24px;
  }
  td a:hover {
    color: var(--green);
  }
  td {
    font-size: 12px;
  }
</style>
