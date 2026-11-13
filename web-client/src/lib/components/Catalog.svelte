<script lang="ts">
  import { onMount, untrack } from "svelte";
  import { page } from "$app/state";
  import { beforeNavigate, goto } from "$app/navigation";
  import Icon from "./Icon.svelte";
  import BookCover from "./BookCover.svelte";
  import ConfirmDialog from "./ConfirmDialog.svelte";
  import { api, post, download, message, statusLabels, plural } from "$lib/api";
  import { selectIds, toggleRange, SELECTION_LIMIT } from "$lib/selection";
  import { canEdit } from "$lib/session.svelte";
  import { rememberCatalog } from "$lib/catalog-context";
  import { validateForm } from "$lib/validation";
  import type { Work, Collection, Page } from "$lib/types";
  let { archive = false }: { archive?: boolean } = $props();
  let works = $state<Work[]>([]),
    collections = $state<Collection[]>([]),
    total = $state(0),
    pages = $state(0),
    pageNumber = $state(0),
    loading = $state(true),
    loaded = $state(false),
    error = $state(""),
    notice = $state(""),
    advanced = $state(false),
    mounted = $state(false);
  let q = $state(""),
    language = $state(""),
    originType = $state(""),
    collectionId = $state(""),
    status = $state(""),
    featured = $state(""),
    complexity = $state(""),
    tag = $state(""),
    minutes = $state<string | number | undefined>(""),
    comparison = $state("gte"),
    sort = $state("title"),
    direction = $state("asc");
  let selected = $state<string[]>([]),
    selecting = $state(false),
    anchor: string | null = null,
    titles = $state<Record<string, string>>({}),
    bulkOperation = $state(""),
    bulkValue = $state(""),
    bulkError = $state(""),
    failures = $state<{ id: string; message: string }[]>([]),
    confirm = $state(false),
    busy = $state(false),
    exporting = $state(false);
  let controller: AbortController | undefined,
    sequence = 0,
    refs: Promise<Collection[]> | undefined,
    searchTimer: ReturnType<typeof setTimeout> | undefined,
    filterKey = "";
  const filterNames = [
    "q",
    "language",
    "originType",
    "collectionId",
    "status",
    "featured",
    "complexity",
    "tag",
    "minutes",
    "comparison",
  ];
  const visibleSelected = $derived(
    works.filter((w) => selected.includes(w.id)).length,
  );
  const hasFilters = $derived(
    !!(
      q ||
      language ||
      originType ||
      collectionId ||
      status ||
      featured ||
      complexity ||
      tag ||
      minutes
    ),
  );
  const actionLabel = $derived(
    bulkOperation === "status"
      ? "Изменить статус"
      : bulkOperation === "addTag"
        ? "Добавить тег"
        : bulkOperation === "removeTag"
          ? "Убрать тег"
          : archive
            ? "Восстановить"
            : "В архив",
  );
  function params() {
    const p = new URLSearchParams({
      page: String(pageNumber),
      size: "12",
      sort,
      direction,
      archive: archive ? "archived" : "active",
    });
    for (const [key, value] of Object.entries({
      q: q.trim(),
      language,
      originType,
      collectionId,
      status,
      featured,
      complexity,
      tag: tag.trim(),
      minutes,
      comparison,
    }))
      if (value !== "" && value != null) p.set(key, String(value));
    return p;
  }
  function restore(query: string) {
    const p = new URLSearchParams(query);
    const nextKey = filterNames
      .filter((k) => k !== "comparison" || p.has("minutes"))
      .map((k) => `${k}=${p.get(k) || ""}`)
      .join("&");
    if (filterKey && filterKey !== nextKey && selected.length) {
      endSelection();
      notice = "Выбор снят: условия поиска изменились.";
    }
    filterKey = nextKey;
    q = p.get("q") || "";
    language = p.get("language") || "";
    originType = p.get("originType") || "";
    collectionId = p.get("collectionId") || "";
    status = p.get("status") || "";
    featured = p.get("featured") || "";
    complexity = p.get("complexity") || "";
    tag = p.get("tag") || "";
    minutes = p.get("minutes") || "";
    comparison = p.get("comparison") || "gte";
    sort = ["title", "author", "createdAt", "targetMinutes"].includes(
      p.get("sort") || "",
    )
      ? p.get("sort")!
      : "title";
    direction = p.get("direction") === "desc" ? "desc" : "asc";
    pageNumber = Math.max(0, Math.floor(Number(p.get("page")) || 0));
    if (
      language ||
      originType ||
      status ||
      featured ||
      complexity ||
      tag ||
      minutes
    )
      advanced = true;
  }
  async function navigate(replace = false) {
    clearTimeout(searchTimer);
    const p = params();
    p.delete("size");
    p.delete("archive");
    if (!minutes) p.delete("comparison");
    if (pageNumber === 0) p.delete("page");
    if (sort === "title") p.delete("sort");
    if (direction === "asc") p.delete("direction");
    if (page.url.searchParams.toString() === p.toString()) await load();
    else
      await goto(`${page.url.pathname}?${p}`, {
        noScroll: true,
        keepFocus: true,
        replaceState: replace,
      });
  }
  async function load() {
    controller?.abort();
    controller = new AbortController();
    const request = ++sequence;
    loading = true;
    error = "";
    anchor = null;
    try {
      refs ??= api<Collection[]>("library/collections").catch((e) => {
        refs = undefined;
        throw e;
      });
      const [result, reference] = await Promise.all([
        api<Page<Work>>(`library/works?${params()}`, {
          signal: controller.signal,
        }),
        refs,
      ]);
      if (request !== sequence) return;
      works = result.content;
      total = result.totalElements;
      pages = result.totalPages;
      collections = reference;
      loaded = true;
      for (const work of works) titles[work.id] = work.title;
      if (pageNumber > 0 && pageNumber >= Math.max(pages, 1)) {
        pageNumber = Math.max(0, pages - 1);
        void navigate(true);
      }
    } catch (e) {
      if (
        request === sequence &&
        !(e instanceof DOMException && e.name === "AbortError")
      )
        error = message(e);
    } finally {
      if (request === sequence) loading = false;
    }
  }
  function search(event?: SubmitEvent, replace = false) {
    event?.preventDefault();
    pageNumber = 0;
    void navigate(replace);
  }
  function typeSearch(event: Event) {
    if (event instanceof InputEvent && event.isComposing) return;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => search(undefined, true), 350);
  }
  function clear() {
    q = "";
    language = "";
    originType = "";
    collectionId = "";
    status = "";
    featured = "";
    complexity = "";
    tag = "";
    minutes = "";
    comparison = "gte";
    search();
  }
  function endSelection() {
    if (busy) return;
    selected = [];
    selecting = false;
    confirm = false;
    anchor = null;
    failures = [];
    bulkError = "";
  }
  function toggle(id: string, shift = false) {
    if (busy || loading) return;
    selecting = true;
    confirm = false;
    bulkError = "";
    const next = toggleRange(
      selected,
      works.map((w) => w.id),
      id,
      anchor,
      shift,
    );
    if (next.length === SELECTION_LIMIT && !next.includes(id))
      bulkError = `Можно выбрать до ${SELECTION_LIMIT} произведений за раз.`;
    selected = next;
    anchor = id;
  }
  function selectPage(checked: boolean) {
    selected = selectIds(
      selected,
      works.map((w) => w.id),
      checked,
    );
    anchor = null;
    if (checked && works.some((w) => !selected.includes(w.id)))
      bulkError = `Можно выбрать до ${SELECTION_LIMIT} произведений за раз.`;
  }
  function changeOperation() {
    bulkValue = bulkOperation === "status" ? "DRAFT" : "";
    bulkError = "";
    failures = [];
  }
  function requestBulk(event: SubmitEvent) {
    event.preventDefault();
    bulkError = "";
    if (!selected.length) return;
    if (
      (bulkOperation === "addTag" || bulkOperation === "removeTag") &&
      !bulkValue.trim()
    ) {
      bulkError = "Укажите тег для выбранных произведений.";
      (event.currentTarget as HTMLFormElement)
        .querySelector<HTMLInputElement>("input")
        ?.focus();
      return;
    }
    if (!archive && bulkOperation === "archive") confirm = true;
    else void bulk();
  }
  async function bulk() {
    if (busy) return;
    busy = true;
    bulkError = "";
    notice = "";
    failures = [];
    try {
      const result = await post<{
        success: string[];
        errors: { id: string; message: string }[];
      }>("library/works/bulk", {
        ids: selected,
        operation: bulkOperation,
        value: bulkValue.trim(),
      });
      selected = selected.filter((id) => !result.success.includes(id));
      failures = result.errors;
      notice = `${actionLabel}: обработано ${plural(result.success.length, "произведение", "произведения", "произведений")}.`;
      confirm = false;
      if (!selected.length) selecting = false;
      await load();
    } catch (e) {
      confirm = false;
      bulkError = message(e);
    } finally {
      busy = false;
    }
  }
  async function exportCsv() {
    exporting = true;
    try {
      await download(
        `library/works/export?${params()}`,
        "Теремок — каталог.csv",
      );
    } catch (e) {
      error = message(e);
    } finally {
      exporting = false;
    }
  }
  $effect(() => {
    const query = page.url.search;
    if (mounted)
      untrack(() => {
        rememberCatalog(page.url.pathname + query, archive);
        restore(query);
        void load();
      });
  });
  onMount(() => {
    mounted = true;
    bulkOperation = archive ? "restore" : "archive";
    return () => {
      controller?.abort();
      sequence++;
      clearTimeout(searchTimer);
    };
  });
  beforeNavigate(({ to }) => {
    if (to?.url.pathname !== page.url.pathname) {
      clearTimeout(searchTimer);
      controller?.abort();
      sequence++;
    }
  });
</script>

<svelte:window
  onkeydown={(e) => {
    if (
      e.key === "Escape" &&
      selecting &&
      !confirm &&
      !busy &&
      !(
        (e.target instanceof HTMLInputElement &&
          !["checkbox", "radio"].includes(e.target.type)) ||
        e.target instanceof HTMLSelectElement ||
        e.target instanceof HTMLTextAreaElement
      )
    ) {
      endSelection();
    }
  }}
/>
<svelte:head
  ><title>{archive ? "Архив" : "Библиотека"} — Теремок</title></svelte:head
>
<div class="page-heading">
  <div>
    <h1>{archive ? "Архив произведений" : "Библиотека"}</h1>
    <p>
      {archive
        ? "Сохранённые произведения, убранные из основного каталога"
        : "Выберите историю, которой хотите подарить голос"}
    </p>
  </div>
  {#if canEdit() && !archive}<a href="/works/new" class="btn"
      ><Icon name="plus" size={17} />Добавить произведение</a
    >{/if}
</div>
<div class="catalog-tabs">
  <span class="tab-active"
    >{archive ? "Архивные" : "Все произведения"} <span>{total}</span></span
  ><span class="catalog-caption">Тексты для совместного озвучивания</span>
</div>
<form inert={busy} class="catalog-filters" onsubmit={search}>
  <div class="search grow">
    <Icon name="search" size={18} /><input
      aria-label="Поиск произведений"
      bind:value={q}
      name="q"
      type="search"
      autocomplete="off"
      placeholder="Название, автор или код…"
      oninput={typeSearch}
      oncompositionend={() => search(undefined, true)}
    />
  </div>
  <select
    aria-label="Коллекция"
    bind:value={collectionId}
    onchange={() => search()}
    ><option value="">Все коллекции</option>{#each collections as item}<option
        value={item.id}>{item.name}</option
      >{/each}</select
  ><button
    type="button"
    class="btn secondary filter-btn"
    class:filter-active={advanced || hasFilters}
    aria-expanded={advanced}
    aria-controls="advanced-filters"
    onclick={() => (advanced = !advanced)}
    ><Icon name="filter" size={16} />Фильтры</button
  ><button class="btn secondary" type="submit">Найти</button>
</form>
{#if advanced}<form
    inert={busy}
    id="advanced-filters"
    class="advanced-filters"
    onsubmit={search}
    use:validateForm
  >
    <label
      >Язык<select bind:value={language}
        ><option value="">Все языки</option><option value="rus">Русский</option
        ><option value="eng">Английский</option><option value="deu"
          >Немецкий</option
        ><option value="fra">Французский</option></select
      ></label
    >
    <label
      >Оригинал или перевод<select bind:value={originType}
        ><option value="">Все произведения</option><option value="ORIGINAL"
          >Оригинал</option
        ><option value="TRANSLATION">Перевод</option></select
      ></label
    >
    <label
      >Статус<select bind:value={status}
        ><option value="">Любой статус</option
        >{#each ["DRAFT", "RECORDING", "READY"] as value}<option {value}
            >{statusLabels[value]}</option
          >{/each}</select
      ></label
    ><label
      >Избранное редакции<select bind:value={featured}
        ><option value="">Все произведения</option><option value="true"
          >Рекомендуемые</option
        ><option value="false">Остальные</option></select
      ></label
    ><label
      >Сложность<select bind:value={complexity}
        ><option value="">Любая сложность</option
        >{#each ["LOW", "MEDIUM", "HIGH"] as value}<option {value}
            >{statusLabels[value]}</option
          >{/each}</select
      ></label
    ><label>Тег<input bind:value={tag} placeholder="Например, сказка" /></label
    ><label
      >Длительность<select bind:value={comparison}
        ><option value="gte">Не менее</option><option value="lte"
          >Не более</option
        ><option value="eq">Ровно</option><option value="gt">Более</option
        ><option value="lt">Менее</option></select
      ></label
    ><label
      >Минут<input
        type="number"
        min="1"
        max="1440"
        bind:value={minutes}
      /></label
    >
    <div class="row">
      <button class="btn">Применить</button><button
        type="button"
        class="btn ghost"
        onclick={clear}>Сбросить</button
      >
    </div>
  </form>{/if}
{#if error}<div class="error row between" role="alert">
    <span>{error}</span><button class="btn secondary" onclick={load}
      >Повторить загрузку</button
    >
  </div>{/if}
{#if notice}<div class="success row between" role="status">
    <span>{notice}</span><button
      class="icon-btn"
      aria-label="Скрыть уведомление"
      onclick={() => (notice = "")}><Icon name="close" size={16} /></button
    >
  </div>{/if}
<div class="catalog-meta">
  <div class="row">
    {#if canEdit()}
      {#if selecting}<label class="check page-select"
          ><input
            type="checkbox"
            aria-label="Выбрать все на странице"
            checked={works.length > 0 && visibleSelected === works.length}
            indeterminate={visibleSelected > 0 &&
              visibleSelected < works.length}
            disabled={busy || loading || !works.length}
            onchange={(e) => selectPage(e.currentTarget.checked)}
          />На этой странице</label
        >
        <button class="btn ghost" disabled={busy} onclick={endSelection}
          >Отменить выбор</button
        >
      {:else}<span>Найдено: <strong>{total}</strong></span><button
          class="btn secondary select-mode"
          disabled={!works.length || loading}
          onclick={() => (selecting = true)}
          ><Icon name="select" size={17} />Выбрать книги</button
        >{/if}
    {:else}<span>Найдено: {total}</span>{/if}
  </div>
  <div class="row sort-controls">
    <label for="catalog-sort">Сортировка:</label><select
      id="catalog-sort"
      aria-label="Сортировка"
      bind:value={sort}
      disabled={busy}
      onchange={() => {
        pageNumber = 0;
        void navigate();
      }}
      ><option value="title">По названию</option><option value="author"
        >По автору</option
      ><option value="createdAt">По дате добавления</option><option
        value="targetMinutes">По длительности</option
      ></select
    >
    <button
      class="icon-btn"
      disabled={busy}
      aria-label={direction === "asc"
        ? "Сортировать по убыванию"
        : "Сортировать по возрастанию"}
      title={direction === "asc"
        ? "По возрастанию. Изменить порядок"
        : "По убыванию. Изменить порядок"}
      onclick={() => {
        direction = direction === "asc" ? "desc" : "asc";
        void navigate();
      }}><Icon name={direction === "asc" ? "up" : "down"} size={18} /></button
    >
    <button
      class="icon-btn"
      disabled={exporting}
      title="Скачать каталог с текущими фильтрами"
      aria-label="Экспорт CSV"
      onclick={exportCsv}
      ><Icon name={exporting ? "loading" : "download"} size={18} /></button
    >
  </div>
</div>
{#if selecting}<p class="selection-hint">
    Нажмите на книгу, чтобы выбрать её. Shift — выбрать диапазон. Выбор
    сохраняется между страницами.<span>Esc — отмена</span>
  </p>{/if}
<section class="catalog-results" aria-label="Произведения" aria-busy={loading}>
  <p class="sr-only" role="status">
    {loading
      ? "Загружаем произведения…"
      : `Найдено: ${total}. Выбрано: ${selected.length}.`}
  </p>
  {#if !loaded && loading}<div class="book-grid" aria-hidden="true">
      {#each Array(6) as _}<div class="book-placeholder">
          <div></div>
          <span></span><span></span>
        </div>{/each}
    </div>
  {:else if !works.length && !error}<div class="empty">
      <Icon name={archive ? "archive" : "book"} size={34} />
      <h2>
        {hasFilters
          ? "Произведения не найдены"
          : archive
            ? "В архиве пока пусто"
            : "Библиотека пока пуста"}
      </h2>
      <p>
        {hasFilters
          ? "Попробуйте другой запрос или сбросьте фильтры."
          : archive
            ? "Здесь появятся произведения, которые вы отправите в архив."
            : "Добавьте первое произведение в библиотеку."}
      </p>
      {#if hasFilters}<button class="btn secondary" onclick={clear}
          >Сбросить фильтры</button
        >{:else if canEdit() && !archive}<a class="btn" href="/works/new"
          >Добавить произведение</a
        >{/if}
    </div>
  {:else}<div class="book-grid" class:refreshing={loading} class:selecting>
      {#each works as work (work.id)}<article
          class="book-card"
          class:selected={selected.includes(work.id)}
        >
          {#if canEdit()}<label
              class="book-check"
              title={selected.includes(work.id)
                ? "Снять выбор"
                : "Выбрать книгу"}
              ><input
                type="checkbox"
                id={`select-book-${work.id}`}
                aria-label={`Выбрать «${work.title}»`}
                checked={selected.includes(work.id)}
                disabled={busy || loading}
                onclick={(e) => {
                  toggle(work.id, e.shiftKey);
                  e.currentTarget.checked = selected.includes(work.id);
                }}
              /></label
            >{/if}
          {#if selecting}<label
              class="cover-link select-cover"
              for={`select-book-${work.id}`}
              ><BookCover title={work.title} author={work.author} /></label
            >
          {:else}<a
              href={`/works/${work.id}`}
              class="cover-link"
              aria-label={`Открыть «${work.title}»`}
              ><BookCover title={work.title} author={work.author} /></a
            >{/if}
          <div class="book-description">
            {#if selecting}<label
                class="book-title"
                for={`select-book-${work.id}`}
                title={work.title}>{work.title}</label
              >{:else}<a
                class="book-title"
                href={`/works/${work.id}`}
                title={work.title}>{work.title}</a
              >{/if}
            <div class="book-author" title={work.author}>{work.author}</div>
            <div class="book-bottom">
              <span class={`badge ${work.status}`}
                >{statusLabels[work.status]}</span
              ><span
                class="role-count"
                title={plural(work.rolesCount, "роль", "роли", "ролей")}
                ><Icon name="users" size={14} />{work.rolesCount}</span
              >
            </div>
          </div>
        </article>{/each}
    </div>{/if}
  {#if loading && loaded}<div class="refresh-indicator" role="status">
      <Icon name="loading" size={16} />Обновляем…
    </div>{/if}
</section>
{#if pages > 1}<nav class="pagination" aria-label="Страницы каталога">
    <span>{plural(total, "произведение", "произведения", "произведений")}</span
    ><button
      class="icon-btn"
      aria-label="Предыдущая страница"
      disabled={pageNumber === 0 || busy || loading}
      onclick={() => {
        pageNumber--;
        void navigate();
      }}><Icon name="left" size={17} /></button
    ><span class="current" aria-current="page">{pageNumber + 1}</span><span
      >из {pages}</span
    ><button
      class="icon-btn"
      aria-label="Следующая страница"
      disabled={pageNumber + 1 >= pages || busy || loading}
      onclick={() => {
        pageNumber++;
        void navigate();
      }}><Icon name="right" size={17} /></button
    >
  </nav>{/if}
{#if selecting}<div class="selection-dock">
    <form class="bulk-bar" onsubmit={requestBulk} novalidate>
      <div class="selection-count" role="status">
        <Icon name="select" size={20} /><strong
          >Выбрано: {selected.length}</strong
        ><span
          >{visibleSelected !== selected.length
            ? `На этой странице: ${visibleSelected}`
            : `До ${SELECTION_LIMIT} за раз`}</span
        >
      </div>
      <div class="bulk-controls">
        <select
          aria-label="Действие с выбранными книгами"
          bind:value={bulkOperation}
          onchange={changeOperation}
          disabled={busy}
          ><option value={archive ? "restore" : "archive"}
            >{archive ? "Восстановить" : "В архив"}</option
          ><option value="status">Изменить статус</option><option value="addTag"
            >Добавить тег</option
          ><option value="removeTag">Убрать тег</option></select
        >
        {#if bulkOperation === "status"}<select
            aria-label="Новый статус"
            bind:value={bulkValue}
            disabled={busy}
            >{#each ["DRAFT", "RECORDING", "READY"] as value}<option {value}
                >{statusLabels[value]}</option
              >{/each}</select
          >{:else if bulkOperation === "addTag" || bulkOperation === "removeTag"}<input
            aria-label="Тег для выбранных книг"
            aria-invalid={!!bulkError}
            aria-describedby={bulkError ? "bulk-error" : undefined}
            bind:value={bulkValue}
            oninput={() => (bulkError = "")}
            disabled={busy}
            maxlength="60"
            placeholder="Название тега…"
          />{/if}
        <button class="btn" disabled={busy || loading || !selected.length}
          >{#if busy}<Icon name="loading" size={17} />{/if}{busy
            ? "Выполняем…"
            : `${actionLabel}${selected.length ? ` (${selected.length})` : ""}`}</button
        >
      </div>
      <button
        type="button"
        class="icon-btn"
        aria-label="Отменить выбор книг"
        title="Отменить выбор · Esc"
        disabled={busy}
        onclick={endSelection}><Icon name="close" size={19} /></button
      >
    </form>
    {#if bulkError}<p class="bulk-error" id="bulk-error" role="alert">
        {bulkError}
      </p>{/if}
    {#if failures.length}<div class="bulk-error" role="alert">
        <strong
          >Не удалось обработать: {failures.length}. Эти книги остались
          выбранными.</strong
        >
        <ul>
          {#each failures as failure}<li>
              {titles[failure.id] || "Произведение"}: {failure.message}
            </li>{/each}
        </ul>
      </div>{/if}
  </div>{/if}
{#if confirm}<ConfirmDialog
    title="Отправить книги в архив?"
    description={`Выбрано: ${plural(selected.length, "произведение", "произведения", "произведений")}. Они исчезнут из библиотеки. Вернуть их можно в разделе «Архив».`}
    label={`В архив (${selected.length})`}
    {busy}
    onconfirm={bulk}
    onclose={() => (confirm = false)}
  />{/if}

<style>
  .catalog-tabs {
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid #dfe5e1;
    margin-bottom: 24px;
    height: 44px;
  }
  .tab-active {
    height: 44px;
    border-bottom: 2px solid var(--green);
    color: #3d744a;
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 12px;
    font-weight: 500;
  }
  .tab-active > span {
    font-size: 10px;
    background: #e9eeea;
    padding: 2px 6px;
    border-radius: 4px;
    color: #526957;
  }
  .catalog-caption {
    font-size: 12px;
    color: var(--muted);
  }
  .catalog-filters {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 22px;
  }
  .catalog-filters select {
    font-size: 12px;
    max-width: 230px;
  }
  .catalog-filters input {
    font-size: 12px;
  }
  .filter-active {
    border-color: #8db395 !important;
    color: var(--green) !important;
  }
  .catalog-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 12px;
    color: var(--muted);
    margin-bottom: 20px;
    min-height: 30px;
  }
  .catalog-meta select {
    font-size: 12px;
    border: 0;
    background-color: transparent;
    padding: 0 30px 0 2px;
    min-height: 28px;
    color: #6b786e;
  }
  .catalog-meta label {
    font-size: 12px;
    color: #526459;
  }
  .sort-direction {
    font-size: 18px;
  }
  .book-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(175px, 1fr));
    gap: 28px 24px;
  }
  .book-card {
    position: relative;
    min-width: 0;
  }
  .cover-link {
    display: block;
    border-radius: 7px;
    outline-offset: 5px;
    transition: transform 0.15s;
  }
  .cover-link:hover {
    transform: translateY(-3px);
  }
  .book-card.selected .cover-link {
    outline: 3px solid var(--green);
    outline-offset: 4px;
  }
  .book-description {
    padding: 13px 1px 0;
  }
  .book-title {
    display: block;
    font-size: 13px;
    line-height: 1.5;
    font-weight: 550;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .book-title:hover {
    color: var(--green);
  }
  .book-author {
    font-size: 12px;
    color: #68756d;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-top: 4px;
    min-height: 16px;
  }
  .book-bottom {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 12px;
  }
  .role-count {
    display: flex;
    align-items: center;
    gap: 4px;
    color: #607168;
    font-size: 12px;
  }
  .book-bottom .badge {
    font-size: 11px;
    padding: 4px 7px;
  }
  .book-check {
    position: absolute;
    z-index: 1;
    top: 8px;
    left: 8px;
    padding: 9px;
    cursor: pointer;
    background: #ffffffd9;
    border-radius: 4px;
    opacity: 0;
    transition: opacity 0.1s;
  }
  .book-check input {
    width: 18px;
    height: 18px;
    margin: 0;
  }
  .book-card:hover .book-check,
  .book-card.selected .book-check,
  .book-check:focus-within {
    opacity: 1;
  }
  .advanced-filters {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;
    background: white;
    border: 1px solid var(--line);
    border-radius: 10px;
    padding: 20px;
  }
  .advanced-filters label {
    font-size: 11px;
  }
  .advanced-filters .row {
    grid-column: span 2;
    justify-content: flex-end;
    align-self: end;
  }
  .empty {
    margin-top: 10px;
  }
  @media (min-width: 1600px) {
    .book-grid {
      grid-template-columns: repeat(6, minmax(0, 1fr));
    }
  }
  @media (max-width: 1200px) {
    .book-grid {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
    .advanced-filters {
      grid-template-columns: repeat(3, 1fr);
    }
  }
  .catalog-meta {
    gap: 16px;
    flex-wrap: wrap;
  }
  .select-mode {
    min-height: 34px;
    padding: 6px 10px;
    font-size: 12px;
  }
  .selection-hint {
    display: flex;
    justify-content: space-between;
    gap: 20px;
    font-size: 12px;
    color: var(--muted);
    margin: -6px 0 22px;
  }
  .selection-hint span {
    white-space: nowrap;
  }
  .select-cover {
    padding: 0;
    width: 100%;
    text-align: inherit;
    cursor: pointer;
  }
  .selecting {
    user-select: none;
  }
  .book-title:is(label) {
    cursor: pointer;
  }
  .select-cover:active:not(:disabled) {
    transform: scale(0.98);
  }
  .book-title {
    width: 100%;
    text-align: left;
    padding: 0;
  }
  .selecting .book-check {
    opacity: 1;
  }
  .book-check {
    box-shadow: 0 1px 6px #11251b22;
  }
  .book-card.selected .book-check {
    background: #fff;
  }
  .catalog-results {
    position: relative;
    min-height: 340px;
  }
  .book-grid {
    transition: opacity 150ms;
  }
  .book-grid.refreshing {
    opacity: 0.48;
    pointer-events: none;
  }
  .refresh-indicator {
    position: absolute;
    top: 16px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: center;
    gap: 8px;
    background: white;
    padding: 9px 14px;
    border-radius: 20px;
    font-size: 12px;
    color: var(--green);
    box-shadow: 0 3px 18px #12251a1a;
  }
  .book-placeholder > div {
    aspect-ratio: 3/4;
    border-radius: 8px;
    background: #e4e9e5;
  }
  .book-placeholder > span {
    display: block;
    margin-top: 14px;
    height: 12px;
    width: 76%;
    border-radius: 4px;
    background: #e4e9e5;
  }
  .book-placeholder > span:last-child {
    width: 48%;
    margin-top: 8px;
  }
  .selection-dock {
    position: sticky;
    bottom: 18px;
    margin-top: 28px;
    z-index: 9;
    border: 1px solid #c5d9cb;
    border-radius: 13px;
    background: #fff;
    box-shadow: 0 8px 30px #1836231c;
    animation: dock-in 160ms ease-out;
  }
  .bulk-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 16px;
  }
  .selection-count {
    display: grid;
    grid-template-columns: 20px auto;
    column-gap: 9px;
    row-gap: 4px;
    font-size: 13px;
    color: var(--green);
  }
  .selection-count :global(.icon) {
    grid-row: span 2;
    align-self: center;
  }
  .selection-count span {
    grid-column: 2;
    color: var(--muted);
    font-size: 11px;
  }
  .bulk-controls {
    display: flex;
    gap: 10px;
    align-items: center;
    flex: 1;
    justify-content: flex-end;
  }
  .bulk-controls select {
    max-width: 200px;
    font-size: 12px;
  }
  .bulk-controls input {
    max-width: 185px;
    font-size: 12px;
  }
  .bulk-error {
    color: #a23632;
    font-size: 12px;
    padding: 0 16px 14px;
    line-height: 1.65;
  }
  .bulk-error ul {
    margin: 6px 0 0;
    padding-left: 18px;
    max-height: 120px;
    overflow: auto;
  }
  @keyframes dock-in {
    from {
      opacity: 0;
      transform: translateY(6px);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
  @media (max-width: 1250px) {
    .bulk-bar {
      flex-wrap: wrap;
    }
    .bulk-controls {
      flex-wrap: wrap;
    }
    .selection-hint span {
      display: none;
    }
  }
</style>
