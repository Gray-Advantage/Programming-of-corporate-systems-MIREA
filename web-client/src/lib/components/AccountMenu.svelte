<script lang="ts">
  import { tick } from "svelte";
  import { afterNavigate, goto } from "$app/navigation";
  import { post, message, roleLabels } from "$lib/api";
  import { session } from "$lib/session.svelte";
  import Icon from "./Icon.svelte";

  let open = $state(false),
    busy = $state(false),
    error = $state("");
  let root: HTMLDivElement, trigger: HTMLButtonElement;
  let menu = $state<HTMLDivElement>(null!);
  const role = $derived(
    session.user?.roles.includes("ADMIN")
      ? "ADMIN"
      : session.user?.roles.includes("EDITOR")
        ? "EDITOR"
        : "READER",
  );
  function close(restore = false) {
    open = false;
    if (restore) trigger?.focus();
  }
  async function show(last = false) {
    open = true;
    await tick();
    const items = menu.querySelectorAll<HTMLElement>('[role="menuitem"]');
    items[last ? items.length - 1 : 0]?.focus();
  }
  function onKey(event: KeyboardEvent) {
    if (event.key === "Escape" && open) {
      event.preventDefault();
      event.stopPropagation();
      close(true);
    } else if (
      ["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key) &&
      open
    ) {
      event.preventDefault();
      const items = [
        ...menu.querySelectorAll<HTMLElement>('[role="menuitem"]'),
      ];
      const index = items.indexOf(document.activeElement as HTMLElement);
      items[
        event.key === "Home"
          ? 0
          : event.key === "End"
            ? items.length - 1
            : (index + (event.key === "ArrowUp" ? -1 : 1) + items.length) %
              items.length
      ]?.focus();
    }
  }
  async function logout() {
    if (busy) return;
    busy = true;
    error = "";
    try {
      await post("session/logout");
      session.user = null;
      await goto("/login");
    } catch (e) {
      error = message(e);
    } finally {
      busy = false;
    }
  }
  afterNavigate(() => close());
</script>

<svelte:window
  onpointerdown={(e) => {
    if (open && !root.contains(e.target as Node)) close();
  }}
/>
<div
  class="account"
  bind:this={root}
  onfocusout={(e) => {
    if (e.relatedTarget && !root.contains(e.relatedTarget as Node)) close();
  }}
>
  <button
    class="user"
    bind:this={trigger}
    aria-label={`Меню пользователя: ${session.user?.name || "Теремок"}`}
    aria-haspopup="menu"
    aria-expanded={open}
    aria-controls="account-menu"
    onclick={() => (open ? close() : show())}
    onkeydown={(e) => {
      onKey(e);
      if (!open && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
        e.preventDefault();
        void show(e.key === "ArrowUp");
      }
    }}
  >
    <span class="avatar"
      >{session.user?.name.slice(0, 1).toUpperCase() || "Т"}</span
    >
    <span class="user-name">{session.user?.name || "Теремок"}</span>
    <span class="chevron" class:open><Icon name="down" size={16} /></span>
  </button>
  {#if open}
    <div
      class="account-menu"
      id="account-menu"
      role="menu"
      tabindex="-1"
      onkeydown={onKey}
      aria-label="Учётная запись"
      bind:this={menu}
    >
      <div class="account-info" role="presentation">
        <strong>{session.user?.name}</strong><span
          >@{session.user?.login} · {roleLabels[role]}</span
        >
      </div>
      <a href="/profile" role="menuitem" tabindex="-1"
        ><Icon name="settings" size={18} />Настройки профиля</a
      >
      <div class="menu-divider" role="separator"></div>
      <button
        role="menuitem"
        tabindex="-1"
        class="logout"
        onclick={logout}
        disabled={busy}
        ><Icon name={busy ? "loading" : "logout"} size={18} />{busy
          ? "Выходим…"
          : "Выйти"}</button
      >
      {#if error}<p class="menu-error" role="alert">{error}</p>{/if}
    </div>
  {/if}
</div>

<style>
  .account {
    position: relative;
  }
  .user {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 9px 6px 6px;
    border-radius: 9px;
    font-size: 13px;
    transition:
      background 150ms,
      transform 150ms;
  }
  .user:hover,
  .user[aria-expanded="true"] {
    background: var(--pale);
  }
  .user:active {
    transform: scale(0.98);
  }
  .user-name {
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .avatar {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: #e3ede5;
    color: #356a44;
    font-weight: 600;
  }
  .chevron {
    display: flex;
    color: var(--muted);
    transition: transform 150ms;
  }
  .chevron.open {
    transform: rotate(180deg);
  }
  .account-menu {
    position: absolute;
    right: 0;
    top: calc(100% + 9px);
    width: 290px;
    z-index: 50;
    background: white;
    border: 1px solid var(--line);
    border-radius: 12px;
    padding: 6px;
    box-shadow:
      0 12px 36px #1933261a,
      0 2px 6px #1933260a;
    animation: appear 140ms ease-out;
    transform-origin: top right;
  }
  .account-info {
    padding: 12px 11px 14px;
    display: grid;
    gap: 6px;
    overflow-wrap: anywhere;
  }
  .account-info strong {
    font-size: 13px;
    font-weight: 600;
  }
  .account-info span {
    color: var(--muted);
    font-size: 11px;
  }
  .account-menu a,
  .account-menu button {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 11px;
    border-radius: 7px;
    font-size: 13px;
    text-align: left;
  }
  .account-menu a:hover,
  .account-menu a:focus-visible {
    color: var(--green);
    background: var(--pale);
  }
  .account-menu .logout {
    color: var(--red);
  }
  .logout:hover,
  .logout:focus-visible {
    background: #fff0ee;
  }
  .account-menu :focus-visible {
    outline-offset: -2px;
  }
  .menu-divider {
    height: 1px;
    background: var(--line);
    margin: 5px;
  }
  .menu-error {
    padding: 8px 11px;
    font-size: 12px;
    color: var(--red);
  }
  @keyframes appear {
    from {
      opacity: 0;
      transform: translateY(-4px) scale(0.98);
    }
    to {
      opacity: 1;
      transform: none;
    }
  }
</style>
