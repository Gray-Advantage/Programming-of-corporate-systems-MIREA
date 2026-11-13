<script lang="ts">
  import { validateForm, setFieldError } from "$lib/validation";
  import { onMount } from "svelte";
  import { api, post, put, message } from "$lib/api";
  import { isAdmin } from "$lib/session.svelte";
  import type { Collection } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  let items = $state<Collection[]>([]),
    error = $state(""),
    notice = $state(""),
    editing = $state(""),
    name = $state(""),
    active = $state(true),
    busy = $state(false),
    showForm = $state(false);
  async function load() {
    try {
      items = await api<Collection[]>("library/collections");
    } catch (e) {
      error = message(e);
    }
  }
  function edit(item?: Collection) {
    editing = item?.id || "";
    name = item?.name || "";
    active = item?.active ?? true;
    showForm = true;
  }
  async function save(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget as HTMLFormElement;
    busy = true;
    error = "";
    try {
      if (editing)
        await put(`library/collections/${editing}`, { name, active });
      else await post("library/collections", { name, active });
      showForm = false;
      notice = "Коллекция сохранена";
      await load();
    } catch (e) {
      if ((e as { status?: number }).status === 409)
        setFieldError(form, "name", message(e));
      else error = message(e);
    } finally {
      busy = false;
    }
  }
  onMount(() => {
    if (isAdmin()) void load();
  });
</script>

<svelte:head><title>Коллекции — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Коллекции</h1>
    <p>Справочник, по которому организована библиотека произведений</p>
  </div>
  {#if isAdmin()}<button class="btn" onclick={() => edit()}
      ><Icon name="plus" size={17} />Добавить коллекцию</button
    >{/if}
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}{#if notice}<div
    class="success"
    role="status"
  >
    {notice}
  </div>{/if}{#if !isAdmin()}<div class="notice">
    Изменять справочник может только администратор.
  </div>{:else}{#if showForm}<form
      onsubmit={save}
      use:validateForm
      aria-busy={busy}
      class="panel collection-form"
    >
      <h2>{editing ? "Редактирование коллекции" : "Новая коллекция"}</h2>
      <div class="row wrap">
        <label class="spacer"
          >Название<input
            required
            maxlength="120"
            name="name"
            bind:value={name}
          /></label
        ><label class="check"
          ><input type="checkbox" bind:checked={active} />Активна</label
        ><button class="btn" disabled={busy}>Сохранить</button><button
          type="button"
          class="btn ghost"
          onclick={() => (showForm = false)}>Отмена</button
        >
      </div>
    </form>{/if}
  <div class="table-wrap">
    <table>
      <thead><tr><th>Название</th><th>Состояние</th><th></th></tr></thead><tbody
        >{#each items as item}<tr
            ><td>{item.name}</td><td
              ><span class:READY={item.active} class="badge"
                >{item.active ? "Активна" : "Отключена"}</span
              ></td
            ><td
              ><button
                class="icon-btn"
                aria-label={`Изменить коллекцию ${item.name}`}
                onclick={() => edit(item)}
                ><Icon name="edit" size={17} /></button
              ></td
            ></tr
          >{/each}</tbody
      >
    </table>
  </div>
  <p class="hint bottom-hint">
    Отключённая коллекция остаётся у существующих произведений, но не
    предлагается при создании новых.
  </p>{/if}

<style>
  .collection-form {
    margin-bottom: 24px;
  }
  .collection-form .row {
    align-items: end;
  }
  .collection-form .check {
    padding-bottom: 12px;
  }
  .bottom-hint {
    margin-top: 20px;
  }
</style>
