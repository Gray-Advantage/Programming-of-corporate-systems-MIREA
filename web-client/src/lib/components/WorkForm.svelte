<script lang="ts">
  import { onMount } from "svelte";
  import { validateForm, setFieldError } from "$lib/validation";
  import { beforeNavigate, goto } from "$app/navigation";
  import Icon from "./Icon.svelte";
  import { api, post, put, message, statusLabels } from "$lib/api";
  import { canEdit } from "$lib/session.svelte";
  import type { Work, Collection } from "$lib/types";
  let { id }: { id?: string } = $props();
  let code = $state(""),
    title = $state(""),
    author = $state(""),
    description = $state(""),
    language = $state("rus"),
    collectionId = $state(""),
    status = $state("DRAFT"),
    targetMinutes = $state(5),
    featured = $state(false),
    tags = $state(""),
    script = $state(""),
    version = $state(0),
    lockedScript = $state(false);
  let collections = $state<Collection[]>([]),
    error = $state(""),
    busy = $state(false),
    loading = $state(true);
  let initial = $state("");
  function snapshot() {
    return JSON.stringify({
      code,
      title,
      author,
      description,
      language,
      collectionId,
      status,
      targetMinutes,
      featured,
      tags,
      script,
    });
  }
  function dirty() {
    return !!initial && snapshot() !== initial;
  }
  beforeNavigate((navigation) => {
    if (
      dirty() &&
      !busy &&
      !window.confirm(
        "Уйти без сохранения? Изменения в произведении будут потеряны.",
      )
    )
      navigation.cancel();
  });
  onMount(() => {
    void (async () => {
      try {
        collections = await api<Collection[]>("library/collections");
        collectionId = collections.find((c) => c.active)?.id || "";
        if (id) {
          const work = await api<Work>(`library/works/${id}`);
          code = work.code;
          title = work.title;
          author = work.author;
          description = work.description;
          language = work.language;
          collectionId = work.collectionId;
          status = work.status;
          targetMinutes = work.targetMinutes;
          featured = work.featured;
          tags = work.tags.join(", ");
          script = work.script;
          version = work.version;
          lockedScript = !!work.script;
        }
        initial = snapshot();
      } catch (e) {
        error = message(e);
      } finally {
        loading = false;
      }
    })();
  });
  async function save(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget as HTMLFormElement;
    busy = true;
    error = "";
    try {
      const input = {
        code,
        title,
        author,
        description,
        language,
        collectionId,
        status,
        targetMinutes,
        featured,
        tags: tags
          .split(",")
          .map((t) => t.trim())
          .filter(Boolean),
        script,
        version,
      };
      const work = id
        ? await put<Work>(`library/works/${id}`, input)
        : await post<Work>("library/works", input);
      initial = snapshot();
      await goto(`/works/${work.id}`);
    } catch (e) {
      const text = message(e);
      if (!id && (e as { status?: number }).status === 409)
        setFieldError(
          form,
          "code",
          "Этот код уже используется. Укажите другой код произведения.",
        );
      else error = text;
    } finally {
      busy = false;
    }
  }
</script>

<svelte:window
  onbeforeunload={(event) => {
    if (dirty() && !busy) event.preventDefault();
  }}
/>

<svelte:head
  ><title>{id ? "Редактирование" : "Новое произведение"} — Теремок</title
  ></svelte:head
>
<a href={id ? `/works/${id}` : "/"} class="back"
  ><Icon name="left" size={14} />{id ? "К произведению" : "В библиотеку"}</a
>
<div class="page-heading">
  <div>
    <h1>{id ? "Редактирование произведения" : "Новое произведение"}</h1>
    <p>Сведения о книге и текст для озвучивания по ролям</p>
  </div>
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}
{#if !canEdit()}<div class="notice">
    Создавать и редактировать произведения может редактор или администратор.
  </div>{:else if loading}<div class="skeleton">
    Загружаем данные…
  </div>{:else if !initial}<button
    class="btn secondary"
    onclick={() => window.location.reload()}>Повторить загрузку формы</button
  >{:else}<form class="panel" onsubmit={save} use:validateForm aria-busy={busy}>
    <fieldset disabled={busy} class="form-fields">
      <h2>Основные сведения</h2>
      <div class="field-grid">
        <label
          >Название<input
            required
            maxlength="240"
            name="title"
            bind:value={title}
            placeholder="Название произведения"
          /></label
        ><label
          >Автор<input
            required
            maxlength="300"
            name="author"
            bind:value={author}
            placeholder="Автор или источник"
          /></label
        ><label
          >Код произведения<input
            required
            maxlength="64"
            spellcheck="false"
            autocomplete="off"
            name="code"
            bind:value={code}
            placeholder="Например, TW-001"
          /><span class="form-help"
            >Уникальный код используется при обновлении через CSV.</span
          ></label
        ><label
          >Коллекция<select
            required
            name="collectionId"
            bind:value={collectionId}
            ><option value="" disabled>Выберите коллекцию</option
            >{#each collections.filter((c) => c.active || c.id === collectionId) as item}<option
                value={item.id}
                >{item.name}{item.active ? "" : " (отключена)"}</option
              >{/each}</select
          ></label
        ><label class="wide"
          >Описание<textarea
            rows="3"
            maxlength="3000"
            name="description"
            bind:value={description}
            placeholder="О чём произведение и что стоит знать перед записью"
          ></textarea></label
        ><label
          >Язык<select name="language" bind:value={language}
            ><option value="rus">Русский</option><option value="eng"
              >Английский</option
            ><option value="deu">Немецкий</option><option value="fra"
              >Французский</option
            ></select
          ></label
        ><label
          >Статус<select name="status" bind:value={status}
            >{#each ["DRAFT", "RECORDING", "READY"] as value}<option {value}
                >{statusLabels[value]}</option
              >{/each}</select
          ></label
        ><label
          >Плановая длительность, минут<input
            required
            type="number"
            min="1"
            max="1440"
            name="targetMinutes"
            bind:value={targetMinutes}
          /></label
        ><label
          >Теги<input
            maxlength="1200"
            data-validation="tags"
            name="tags"
            bind:value={tags}
            placeholder="Сказка, короткое, для начинающих"
          /><span class="form-help"
            >Через запятую; до 20 тегов по 60 символов.</span
          ></label
        ><label class="check wide"
          ><input type="checkbox" bind:checked={featured} />Рекомендовано
          редакцией</label
        >
      </div>
      <div class="divider"></div>
      <h2>Сценарий</h2>
      <p class="hint">
        Каждая непустая строка — отдельная реплика. Начинайте строку с имени
        роли и двоеточия. Строки без имени относятся к рассказчику.
      </p>
      <label class="script-label"
        ><span class="sr-only">Текст сценария</span><textarea
          rows="12"
          name="script"
          bind:value={script}
          readonly={lockedScript}
          maxlength="200000"
          placeholder={"Рассказчик: Стоит в поле теремок.\nМышка: Кто в теремочке живёт?"}
        ></textarea></label
      >{#if lockedScript}<p class="hint">
          Сценарий сохранён с постоянными идентификаторами реплик. Для другого
          текста создайте новое произведение: так записи существующих ролей
          сохранят свои связи.
        </p>{/if}
    </fieldset>
    <div class="form-actions">
      <a
        href={id ? `/works/${id}` : "/"}
        class="btn secondary"
        aria-disabled={busy}
        onclick={(e) => {
          if (busy) e.preventDefault();
        }}>Отмена</a
      ><button class="btn" disabled={busy}
        >{busy
          ? "Сохраняем…"
          : id
            ? "Сохранить изменения"
            : "Создать произведение"}</button
      >
    </div>
  </form>{/if}

<style>
  .panel {
    max-width: 1000px;
  }
  .script-label {
    margin: 16px 0;
  }
  .script-label textarea {
    font-size: 13px;
    background: #fcfdfb;
  }
  .script-label textarea:read-only {
    color: #7a827a;
    background: #f7f9f7;
  }
</style>
