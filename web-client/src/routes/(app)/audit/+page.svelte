<script lang="ts">
  import { onMount } from "svelte";
  import { api, message, date } from "$lib/api";
  import { isAdmin } from "$lib/session.svelte";
  import Icon from "$lib/components/Icon.svelte";
  let rows = $state<any[]>([]),
    source = $state("library"),
    q = $state(""),
    direction = $state("desc"),
    pageNumber = $state(0),
    total = $state(0),
    error = $state(""),
    loading = $state(true);
  const events: Record<string, string> = {
    LOGIN: "Вход",
    LOGIN_FAILED: "Неудачный вход",
    LOGOUT: "Выход",
    PASSWORD_CHANGED: "Смена пароля",
    USER_CREATED: "Создан пользователь",
    USER_UPDATED: "Изменён пользователь",
    WORK_CREATED: "Добавлено произведение",
    WORK_UPDATED: "Изменено произведение",
    WORK_ARCHIVE: "Архивирование",
    WORK_RESTORE: "Восстановление",
    WORK_DELETED: "Удалено произведение",
    WORK_STATUS: "Изменён статус",
    WORK_ADDTAG: "Добавлен тег",
    WORK_REMOVETAG: "Удалён тег",
    NOTE_CREATED: "Добавлена заметка",
    NOTE_UPDATED: "Изменена заметка",
    NOTE_DELETED: "Удалена заметка",
    COLLECTION_CREATED: "Добавлена коллекция",
    COLLECTION_UPDATED: "Изменена коллекция",
  };
  async function load() {
    if (!isAdmin()) {
      loading = false;
      return;
    }
    loading = true;
    error = "";
    try {
      const p = new URLSearchParams({
        q,
        page: String(pageNumber),
        size: "15",
        direction,
      });
      const result = await api(
        `${source === "library" ? "library" : "auth"}/audit?${p}`,
      );
      rows = result.content;
      total = result.totalElements;
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  function search(event?: SubmitEvent) {
    event?.preventDefault();
    pageNumber = 0;
    void load();
  }
  onMount(() => {
    void load();
  });
</script>

<svelte:head><title>Журнал аудита — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Журнал аудита</h1>
    <p>История входов и важных изменений данных</p>
  </div>
  <button class="btn secondary" onclick={load}
    ><Icon name="refresh" size={17} />Обновить</button
  >
</div>
{#if !isAdmin()}<div class="notice">
    Журнал доступен только администратору.
  </div>{:else}<div class="tabs">
    <button
      class:active={source === "library"}
      onclick={() => {
        source = "library";
        search();
      }}>Произведения и заметки</button
    ><button
      class:active={source === "auth"}
      onclick={() => {
        source = "auth";
        search();
      }}>Авторизация и пользователи</button
    >
  </div>
  <form class="toolbar" onsubmit={search}>
    <div class="search grow">
      <Icon name="search" size={18} /><input
        aria-label="Поиск по журналу"
        bind:value={q}
        placeholder="Пользователь, описание или тип события"
      />
    </div>
    <select
      aria-label="Порядок событий"
      bind:value={direction}
      onchange={() => search()}
      ><option value="desc">Сначала новые</option><option value="asc"
        >Сначала старые</option
      ></select
    ><button class="btn secondary">Найти</button>
  </form>
  {#if error}<div class="error" role="alert">{error}</div>{/if}{#if loading}<div
      class="skeleton"
    >
      Загружаем события…
    </div>{:else}<div class="table-wrap">
      <table>
        <thead
          ><tr
            ><th>Время</th><th>Участник</th><th>Событие</th><th>Описание</th
            ></tr
          ></thead
        ><tbody
          >{#each rows as row}<tr
              ><td class="time">{date(row.occurred_at)}</td><td
                >{row.actor || "Гость"}</td
              ><td
                ><span class="badge"
                  >{events[row.event_type] || "Изменение данных"}</span
                ></td
              ><td
                >{row.description}
                <details>
                  <summary>Данные запроса</summary>
                  <div class="sub">
                    {row.entity_type === "USER"
                      ? "Пользователь"
                      : row.entity_type === "COLLECTION"
                        ? "Коллекция"
                        : "Произведение"}: {row.entity_id || "—"}
                  </div>
                  <div class="sub">Корреляция: {row.correlation_id}</div>
                </details></td
              ></tr
            >{:else}<tr><td colspan="4">Событий не найдено</td></tr
            >{/each}</tbody
        >
      </table>
    </div>
    <nav class="pagination">
      <span>Всего событий: {total}</span><button
        class="icon-btn"
        aria-label="Предыдущая страница"
        disabled={!pageNumber}
        onclick={() => {
          pageNumber--;
          void load();
        }}><Icon name="left" size={17} /></button
      ><span>{pageNumber + 1} из {Math.max(1, Math.ceil(total / 15))}</span
      ><button
        class="icon-btn"
        aria-label="Следующая страница"
        disabled={(pageNumber + 1) * 15 >= total}
        onclick={() => {
          pageNumber++;
          void load();
        }}><Icon name="right" size={17} /></button
      >
    </nav>{/if}{/if}

<style>
  td {
    font-size: 11px;
  }
  .time {
    white-space: nowrap;
    color: #8b978d;
  }
  details {
    margin-top: 8px;
    color: #a4afa6;
  }
  summary {
    font-size: 9px;
    cursor: pointer;
  }
  details .sub {
    font-size: 9px;
    max-width: 300px;
  }
  .badge {
    font-size: 9px;
  }
</style>
