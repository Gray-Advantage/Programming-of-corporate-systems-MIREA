<script lang="ts">
  import { validateForm, setFieldError } from "$lib/validation";
  import { onMount } from "svelte";
  import { api, post, put, message, roleLabels } from "$lib/api";
  import { isAdmin, type User } from "$lib/session.svelte";
  import type { Page } from "$lib/types";
  import Icon from "$lib/components/Icon.svelte";
  let users = $state<User[]>([]),
    pageNumber = $state(0),
    pages = $state(0),
    q = $state(""),
    editing = $state<string | null>(null),
    showForm = $state(false),
    login = $state(""),
    name = $state(""),
    roles = $state(["READER"]),
    active = $state(true),
    password = $state(""),
    error = $state(""),
    notice = $state(""),
    busy = $state(false),
    loading = $state(true);
  async function load() {
    if (!isAdmin()) {
      loading = false;
      return;
    }
    loading = true;
    error = "";
    try {
      const data = await api<Page<User>>(
        `admin/users?q=${encodeURIComponent(q)}&page=${pageNumber}&size=12`,
      );
      users = data.content;
      pages = data.totalPages;
    } catch (e) {
      error = message(e);
    } finally {
      loading = false;
    }
  }
  function edit(user?: User) {
    editing = user?.id || null;
    login = user?.login || "";
    name = user?.name || "";
    roles = user ? [...user.roles] : ["READER"];
    active = user?.active ?? true;
    password = "";
    showForm = true;
  }
  async function save(event: SubmitEvent) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget as HTMLFormElement;
    if (!roles.length) {
      error = "Выберите хотя бы один уровень доступа.";
      form.querySelector<HTMLInputElement>('input[type="checkbox"]')?.focus();
      return;
    }
    busy = true;
    error = "";
    try {
      const data = { login, name, roles, active, password };
      if (editing) await put(`admin/users/${editing}`, data);
      else await post("admin/users", data);
      notice = editing
        ? "Изменения сохранены. Существующие refresh-сессии пользователя отозваны."
        : "Пользователь создан. При первом входе потребуется сменить временный пароль.";
      showForm = false;
      await load();
    } catch (e) {
      if ((e as { status?: number }).status === 409)
        setFieldError(form, "login", message(e));
      else error = message(e);
    } finally {
      busy = false;
    }
  }
  onMount(() => {
    void load();
  });
</script>

<svelte:head><title>Пользователи — Теремок</title></svelte:head>
<div class="page-heading">
  <div>
    <h1>Пользователи</h1>
    <p>Учётные записи и права доступа к библиотеке</p>
  </div>
  {#if isAdmin()}<button class="btn" onclick={() => edit()}
      ><Icon name="plus" size={17} />Создать пользователя</button
    >{/if}
</div>
{#if error}<div class="error" role="alert">{error}</div>{/if}{#if notice}<div
    class="success"
    role="status"
  >
    {notice}
  </div>{/if}{#if !isAdmin()}<div class="notice">
    Раздел доступен администратору.
  </div>{:else}<div class="users-layout" class:with-form={showForm}>
    <div>
      <form
        class="toolbar"
        onsubmit={(event) => {
          event.preventDefault();
          pageNumber = 0;
          void load();
        }}
      >
        <div class="search grow">
          <Icon name="search" size={18} /><input
            aria-label="Поиск пользователей"
            bind:value={q}
            placeholder="Поиск по имени или логину"
          />
        </div>
        <button class="btn secondary">Найти</button>
      </form>
      {#if loading}<div class="skeleton">
          Загружаем пользователей…
        </div>{:else}<div class="table-wrap">
          <table>
            <thead
              ><tr
                ><th>Пользователь</th><th>Доступ</th><th>Состояние</th><th
                ></th></tr
              ></thead
            ><tbody
              >{#each users as user}<tr
                  ><td
                    ><strong>{user.name}</strong>
                    <div class="sub">{user.login}</div></td
                  ><td
                    ><div class="row wrap">
                      {#each user.roles as role}<span class="badge"
                          >{roleLabels[role]}</span
                        >{/each}
                    </div></td
                  ><td
                    ><span class:READY={user.active} class="badge"
                      >{user.active ? "Активен" : "Отключён"}</span
                    >{#if user.mustChangePassword}<div class="sub">
                        Временный пароль
                      </div>{/if}</td
                  ><td
                    ><button
                      class="icon-btn"
                      aria-label={`Изменить пользователя ${user.name}`}
                      onclick={() => edit(user)}
                      ><Icon name="edit" size={17} /></button
                    ></td
                  ></tr
                >{:else}<tr><td colspan="4">Пользователи не найдены</td></tr
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
        </nav>{/if}
    </div>
    {#if showForm}<form
        class="panel stack user-form"
        onsubmit={save}
        use:validateForm
        aria-busy={busy}
      >
        <div class="row between">
          <h2>{editing ? "Изменить пользователя" : "Новый пользователь"}</h2>
          <button
            type="button"
            class="icon-btn"
            aria-label="Закрыть форму"
            onclick={() => (showForm = false)}
            ><Icon name="close" size={17} /></button
          >
        </div>
        <label
          >Имя<input
            required
            maxlength="160"
            name="name"
            bind:value={name}
          /></label
        ><label
          >Логин<input
            required
            maxlength="100"
            spellcheck="false"
            name="login"
            bind:value={login}
            autocomplete="off"
          /></label
        >
        <fieldset>
          <legend>Уровни доступа</legend
          >{#each ["READER", "EDITOR", "ADMIN"] as value}<label class="check"
              ><input type="checkbox" bind:group={roles} {value} />{roleLabels[
                value
              ]}</label
            >{/each}
        </fieldset>
        <label class="check"
          ><input type="checkbox" bind:checked={active} />Учётная запись активна</label
        ><label
          >{editing ? "Сбросить пароль" : "Временный пароль"}<input
            required={!editing}
            type="password"
            data-validation="password"
            name="password"
            bind:value={password}
            autocomplete="new-password"
            placeholder={editing
              ? "Оставьте пустым, чтобы сохранить"
              : "Укажите временный пароль"}
          /><span class="form-help"
            >От 10 символов, прописная и строчная буквы, цифра. При входе
            пользователь заменит пароль.</span
          ></label
        ><button class="btn" disabled={busy}>Сохранить</button>
        <p class="hint">
          Читатель просматривает данные. Редактор управляет произведениями и
          озвучкой. Администратор также управляет участниками и справочником.
        </p>
      </form>{/if}
  </div>{/if}

<style>
  .users-layout {
    display: grid;
    grid-template-columns: 1fr;
    gap: 24px;
    align-items: start;
  }
  .users-layout.with-form {
    grid-template-columns: minmax(0, 1fr) 310px;
  }
  .user-form h2 {
    font-size: 16px;
  }
  .user-form {
    padding: 20px;
  }
  .user-form .hint {
    font-size: 10px;
  }
  td strong {
    font-size: 12px;
    font-weight: 500;
  }
  fieldset {
    padding: 0;
    border: 0;
    margin: 0;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  legend {
    font-size: 12px;
    font-weight: 500;
    margin-bottom: 12px;
  }
  fieldset label {
    font-size: 12px;
  }
  table .badge {
    font-size: 10px;
  }
  @media (max-width: 1250px) {
    .users-layout.with-form {
      grid-template-columns: 1fr;
    }
    .user-form {
      max-width: 550px;
    }
  }
</style>
