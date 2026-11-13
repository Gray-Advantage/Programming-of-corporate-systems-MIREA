<script lang="ts">
  import { onMount } from "svelte";
  import { api, download, message } from "$lib/api";
  import { isAdmin } from "$lib/session.svelte";
  import type { Collection } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  const serviceLabels = [
    ["auth-service", "Участники и учётные записи"],
    ["catalog-service", "Каталог и управление библиотекой"],
    ["text-work-content-service", "Тексты, части и роли"],
    ["draft-recordings-service", "Черновики записей"],
    ["recordings-service", "Опубликованные роли и сборки"],
  ];
  let q = $state(""),
    collectionId = $state(""),
    status = $state(""),
    archive = $state("active"),
    collections = $state<Collection[]>([]),
    services = $state(serviceLabels.map(([id]) => id)),
    csvError = $state(""),
    excelError = $state(""),
    csvNotice = $state(""),
    excelNotice = $state(""),
    csvBusy = $state(false),
    excelBusy = $state(false);
  async function csv() {
    if (csvBusy) return;
    csvBusy = true;
    csvError = "";
    csvNotice = "";
    try {
      await download(
        `library/works/export?${new URLSearchParams({ q, collectionId, status, archive })}`,
        "Теремок — каталог.csv",
      );
      csvNotice = "Файл каталога скачан.";
    } catch (e) {
      csvError = message(e);
    } finally {
      csvBusy = false;
    }
  }
  async function excel() {
    if (excelBusy || !services.length) return;
    excelBusy = true;
    excelError = "";
    excelNotice = "";
    try {
      const p = new URLSearchParams();
      services.forEach((s) => p.append("services", s));
      const failed = await download(
        `export/xlsx?${p}`,
        "Теремок — данные.xlsx",
      );
      excelNotice = failed
        ? `Файл скачан. Не удалось получить таблицы: ${failed}. Подробности — на листе «_export_status».`
        : "Excel-файл со всеми полями выбранных таблиц скачан.";
    } catch (e) {
      excelError = message(e);
    } finally {
      excelBusy = false;
    }
  }
  onMount(() => {
    void api<Collection[]>("library/collections")
      .then((r) => (collections = r))
      .catch((e) => (csvError = message(e)));
  });
</script>

<svelte:head><title>Экспорт данных — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Экспорт данных</h1>
    <p>Скачайте каталог в CSV или таблицы системы в Excel</p>
  </div>
</div>
<div class="exchange-grid">
  <section class="panel">
    <div class="section-title">
      <Icon name="book" size={23} />
      <h2>Каталог в CSV</h2>
    </div>
    <p class="hint">
      Все поля произведений, включая идентификаторы, даты, статус и сценарий. В
      файл попадут записи с выбранными условиями.
    </p>
    <form
      class="stack"
      onsubmit={(e) => {
        e.preventDefault();
        void csv();
      }}
      aria-busy={csvBusy}
    >
      <fieldset disabled={csvBusy} class="form-fields stack">
        <label
          >Поиск<input
            type="search"
            name="q"
            autocomplete="off"
            bind:value={q}
            placeholder="Название, автор или код…"
          /></label
        >
        <label
          >Коллекция<select bind:value={collectionId}
            ><option value="">Все коллекции</option
            >{#each collections as c}<option value={c.id}>{c.name}</option
              >{/each}</select
          ></label
        >
        <div class="field-grid">
          <label
            >Статус<select bind:value={status}
              ><option value="">Все статусы</option><option value="DRAFT"
                >Черновик</option
              ><option value="RECORDING">В записи</option><option value="READY"
                >Готово</option
              ></select
            ></label
          ><label
            >Архив<select bind:value={archive}
              ><option value="active">Активные</option><option value="archived"
                >Архивные</option
              ><option value="all">Все произведения</option></select
            ></label
          >
        </div>
      </fieldset>
      {#if csvError}<div class="error" role="alert">
          {csvError}
        </div>{/if}{#if csvNotice}<p class="success" role="status">
          {csvNotice}
        </p>{/if}
      <button class="btn secondary" disabled={csvBusy}
        ><Icon name={csvBusy ? "loading" : "download"} size={17} />{csvBusy
          ? "Готовим CSV…"
          : "Скачать CSV"}</button
      >
    </form>
  </section>
  <section class="panel">
    <div class="section-title">
      <Icon name="file" size={23} />
      <h2>Таблицы в Excel</h2>
    </div>
    <p class="hint">
      Каждая таблица — на отдельном листе, со всеми её полями. Сводка выгрузки
      находится на первом листе файла.
    </p>
    <fieldset class="form-fields service-options" disabled={excelBusy}>
      <legend class="sr-only">Разделы системы для выгрузки</legend>
      <label class="check select-all"
        ><input
          type="checkbox"
          checked={services.length === serviceLabels.length}
          indeterminate={services.length > 0 &&
            services.length < serviceLabels.length}
          onchange={(e) =>
            (services = e.currentTarget.checked
              ? serviceLabels.map(([id]) => id)
              : [])}
        />Все разделы</label
      >
      {#each serviceLabels as [value, label]}<label class="check"
          ><input type="checkbox" bind:group={services} {value} />{label}</label
        >{/each}
    </fieldset>
    {#if !isAdmin()}<p class="hint audit-hint">
        Журнал аудита не входит в эту выгрузку: по заданию он доступен только
        администратору.
      </p>{/if}
    {#if !services.length}<p class="hint" role="status">
        Выберите хотя бы один раздел для выгрузки.
      </p>{/if}
    {#if excelError}<div class="error" role="alert">
        {excelError}
      </div>{/if}{#if excelNotice}<p class="success" role="status">
        {excelNotice}
      </p>{/if}
    <button
      class="btn secondary"
      disabled={excelBusy || !services.length}
      onclick={excel}
      ><Icon name={excelBusy ? "loading" : "download"} size={17} />{excelBusy
        ? "Готовим Excel…"
        : "Скачать Excel"}</button
    >
  </section>
</div>

<style>
  .exchange-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    align-items: start;
  }
  .section-title {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--green);
    margin-bottom: 16px;
  }
  .section-title h2 {
    font-size: 18px;
    color: #364b3c;
  }
  .panel > .hint {
    margin-bottom: 24px;
  }
  .service-options {
    display: flex;
    flex-direction: column;
    gap: 18px;
    margin: 26px 0;
  }
  .service-options label {
    font-size: 13px;
  }
  .select-all {
    padding-bottom: 18px;
    border-bottom: 1px solid var(--line);
    font-weight: 500;
  }
  .audit-hint {
    font-size: 12px;
  }
  .stack .error,
  .stack .success {
    margin: 0;
  }
  @media (max-width: 1100px) {
    .exchange-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
