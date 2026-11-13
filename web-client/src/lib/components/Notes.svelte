<script lang="ts">
  import { onMount, tick } from "svelte";
  import { api, post, put, remove, date, message } from "$lib/api";
  import { validateForm, clearFormErrors } from "$lib/validation";
  import { canEdit, isAdmin, session } from "$lib/session.svelte";
  import Icon from "./Icon.svelte";
  import ConfirmDialog from "./ConfirmDialog.svelte";
  interface Note {
    id: string;
    author_id: string;
    author_name: string;
    content: string;
    pinned: boolean;
    created_at: string;
  }
  let { id }: { id: string } = $props();
  let notes = $state<Note[]>([]),
    content = $state(""),
    pinned = $state(false),
    editing = $state(""),
    error = $state(""),
    notice = $state(""),
    busy = $state(false),
    loading = $state(true),
    pendingDelete = $state("");
  let textarea = $state<HTMLTextAreaElement>(null!);
  let original = "",
    originalPinned = false,
    draft = { content: "", pinned: false };
  async function load() {
    error = "";
    try {
      notes = await api(`library/works/${id}/notes`);
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  async function edit(note: Note) {
    if (busy) return;
    if (editing === note.id) {
      textarea.focus();
      return;
    }
    if (
      editing &&
      (content !== original || pinned !== originalPinned) &&
      !window.confirm(
        "Перейти к другой заметке? Несохранённые изменения будут потеряны.",
      )
    )
      return;
    if (!editing) draft = { content, pinned };
    editing = note.id;
    original = note.content;
    originalPinned = note.pinned;
    content = note.content;
    pinned = note.pinned;
    error = "";
    notice = "";
    await tick();
    if (textarea.form) clearFormErrors(textarea.form);
    textarea.focus();
    textarea.scrollIntoView({ block: "center" });
  }
  function cancel() {
    editing = "";
    content = draft.content;
    pinned = draft.pinned;
  }
  async function save(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    busy = true;
    error = "";
    notice = "";
    try {
      const wasEditing = !!editing;
      if (editing)
        await put(`library/works/${id}/notes/${editing}`, {
          content: content.trim(),
          pinned,
        });
      else
        await post(`library/works/${id}/notes`, {
          content: content.trim(),
          pinned,
        });
      if (wasEditing) cancel();
      else {
        content = "";
        pinned = false;
      }
      notice = wasEditing
        ? "Изменения в заметке сохранены."
        : "Заметка добавлена.";
      await load();
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  async function del() {
    if (busy) return;
    busy = true;
    error = "";
    try {
      const deleted = pendingDelete;
      await remove(`library/works/${id}/notes/${deleted}`);
      pendingDelete = "";
      if (editing === deleted) cancel();
      notice = "Заметка удалена.";
      await load();
    } catch (e) {
      pendingDelete = "";
      error = message(e);
    } finally {
      busy = false;
    }
  }
  onMount(() => {
    void load();
  });
</script>

<section class="panel" aria-busy={loading}>
  <h2>Заметки <span class="muted">{notes.length || ""}</span></h2>
  {#if error}<div class="error row between" role="alert">
      <span>{error}</span><button
        class="btn ghost"
        disabled={busy}
        onclick={load}>Обновить заметки</button
      >
    </div>{/if}
  {#if notice}<p class="success" role="status">{notice}</p>{/if}
  {#if loading}<p class="hint" role="status">Загружаем заметки…</p>
  {:else}{#each notes as note (note.id)}<article
        class="note"
        class:editing={editing === note.id}
      >
        <div class="row between wrap">
          <div class="row wrap note-meta">
            <strong>{note.author_name}</strong><small
              >{date(note.created_at)}</small
            >{#if note.pinned}<span class="badge">Закреплено</span>{/if}
          </div>
          {#if canEdit() && (note.author_id === session.user?.id || isAdmin())}<div
              class="row note-actions"
            >
              <button
                class="icon-btn"
                disabled={busy}
                aria-label={`Редактировать заметку: ${note.content.slice(0, 40)}`}
                title="Редактировать заметку"
                onclick={() => edit(note)}
                ><Icon name="edit" size={17} /></button
              >
              <button
                class="icon-btn"
                disabled={busy}
                aria-label={`Удалить заметку: ${note.content.slice(0, 40)}`}
                title="Удалить заметку"
                onclick={() => (pendingDelete = note.id)}
                ><Icon name="trash" size={17} /></button
              >
            </div>{/if}
        </div>
        <p>{note.content}</p>
      </article>{:else}<p class="hint">
        Заметок пока нет. Оставьте пояснения для участников озвучивания.
      </p>{/each}{/if}
  {#if canEdit()}<form
      onsubmit={save}
      class="note-form"
      use:validateForm
      aria-busy={busy}
    >
      <label
        >{editing ? "Редактирование заметки" : "Новая заметка"}<textarea
          name="content"
          required
          disabled={busy}
          rows="3"
          maxlength="3000"
          bind:this={textarea}
          bind:value={content}
          placeholder="Пояснение к роли, пожелание к записи…"></textarea></label
      >
      <div class="row between">
        <label class="check"
          ><input
            type="checkbox"
            disabled={busy}
            bind:checked={pinned}
          />Закрепить</label
        >
        <div class="row">
          {#if editing}<button
              type="button"
              class="btn ghost"
              disabled={busy}
              onclick={cancel}>Отмена</button
            >{/if}
          <button class="btn secondary" disabled={busy}
            >{#if busy}<Icon name="loading" size={16} />{/if}{busy
              ? "Сохраняем…"
              : editing
                ? "Сохранить заметку"
                : "Добавить заметку"}</button
          >
        </div>
      </div>
    </form>{/if}
</section>
{#if pendingDelete}<ConfirmDialog
    title="Удалить заметку?"
    description="Заметка будет удалена у всех участников. Вернуть её после удаления не получится."
    label="Удалить заметку"
    danger
    {busy}
    onconfirm={del}
    onclose={() => (pendingDelete = "")}
  />{/if}

<style>
  .note {
    padding: 18px 0;
    border-bottom: 1px solid var(--line);
  }
  .note.editing {
    border-bottom-color: #8aac94;
  }
  .note strong {
    font-size: 13px;
    font-weight: 500;
  }
  .note small {
    font-size: 11px;
  }
  .note p {
    font-size: 13px;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    margin-top: 10px;
  }
  .note-meta {
    gap: 10px;
  }
  .note-actions {
    gap: 4px;
  }
  .note-form {
    margin-top: 22px;
  }
  .note-form > .row {
    margin-top: 12px;
  }
  .note-form .check {
    font-size: 12px;
  }
</style>
